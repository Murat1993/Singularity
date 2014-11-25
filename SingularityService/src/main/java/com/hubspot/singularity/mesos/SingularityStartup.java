package com.hubspot.singularity.mesos;

import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.hubspot.mesos.JavaUtils;
import com.hubspot.mesos.MesosUtils;
import com.hubspot.mesos.client.MesosClient;
import com.hubspot.mesos.json.MesosMasterStateObject;
import com.hubspot.singularity.SingularityDeployKey;
import com.hubspot.singularity.SingularityPendingDeploy;
import com.hubspot.singularity.SingularityPendingRequest;
import com.hubspot.singularity.SingularityPendingRequest.PendingType;
import com.hubspot.singularity.SingularityRequest;
import com.hubspot.singularity.SingularityRequestDeployState;
import com.hubspot.singularity.SingularityRequestWithState;
import com.hubspot.singularity.SingularityTask;
import com.hubspot.singularity.SingularityTaskHistoryUpdate;
import com.hubspot.singularity.SingularityTaskHistoryUpdate.SimplifiedTaskState;
import com.hubspot.singularity.SingularityTaskId;
import com.hubspot.singularity.data.DeployManager;
import com.hubspot.singularity.data.RequestManager;
import com.hubspot.singularity.data.TaskManager;
import com.hubspot.singularity.data.transcoders.SingularityTaskTranscoder;
import com.hubspot.singularity.data.zkmigrations.ZkDataMigrationRunner;
import com.hubspot.singularity.scheduler.SingularityHealthchecker;
import com.hubspot.singularity.scheduler.SingularityNewTaskChecker;
import com.hubspot.singularity.scheduler.SingularityTaskReconciliation;

@Singleton
class SingularityStartup {

  private static final Logger LOG = LoggerFactory.getLogger(SingularityStartup.class);

  private final MesosClient mesosClient;
  private final TaskManager taskManager;
  private final RequestManager requestManager;
  private final DeployManager deployManager;
  private final SingularitySlaveAndRackManager slaveAndRackManager;
  private final SingularityHealthchecker healthchecker;
  private final SingularityNewTaskChecker newTaskChecker;
  private final SingularityTaskTranscoder taskTranscoder;
  private final SingularityTaskReconciliation taskReconciliation;
  private final ZkDataMigrationRunner zkDataMigrationRunner;

  @Inject
  SingularityStartup(MesosClient mesosClient, SingularityTaskTranscoder taskTranscoder, SingularityHealthchecker healthchecker, SingularityNewTaskChecker newTaskChecker,
      SingularitySlaveAndRackManager slaveAndRackManager, TaskManager taskManager, RequestManager requestManager, DeployManager deployManager, SingularityTaskReconciliation taskReconciliation,
      ZkDataMigrationRunner zkDataMigrationRunner) {
    this.mesosClient = mesosClient;
    this.zkDataMigrationRunner = zkDataMigrationRunner;
    this.slaveAndRackManager = slaveAndRackManager;
    this.deployManager = deployManager;
    this.requestManager = requestManager;
    this.newTaskChecker = newTaskChecker;
    this.taskManager = taskManager;
    this.healthchecker = healthchecker;
    this.taskTranscoder = taskTranscoder;
    this.taskReconciliation = taskReconciliation;
  }

  public void startup(MasterInfo masterInfo, SchedulerDriver driver) throws Exception {
    final long start = System.currentTimeMillis();

    final String uri = mesosClient.getMasterUri(MesosUtils.getMasterHostAndPort(masterInfo));

    LOG.info("Starting up... fetching state data from: " + uri);

    zkDataMigrationRunner.checkMigrations();

    MesosMasterStateObject state = mesosClient.getMasterState(uri);

    slaveAndRackManager.loadSlavesAndRacksFromMaster(state);

    checkSchedulerForInconsistentState();

    enqueueHealthAndNewTaskChecks();

    taskReconciliation.startReconciliation();

    LOG.info("Finished startup after {}", JavaUtils.duration(start));
  }

  /**
   * We need to run this check for the various situations where the scheduler could get in an inconsistent state due
   * to a crash/network failure during series of state transactions.
   *
   *  1) Unpausing
   *  2) Launching Task
   *
   */
  @VisibleForTesting
  void checkSchedulerForInconsistentState() {
    for (SingularityRequestWithState requestWithState : requestManager.getRequests()) {
      switch (requestWithState.getState()) {
        case ACTIVE:
        case SYSTEM_COOLDOWN:
        case DEPLOYING_TO_UNPAUSE:
          checkActiveRequest(requestWithState.getRequest());
          break;
        case DELETED:
        case PAUSED:
        case FINISHED:
          break;
      }
    }
  }

  private void checkActiveRequest(SingularityRequest request) {
    if (request.isOneOff()) {
      return;
    }

    Optional<SingularityRequestDeployState> requestDeployState = deployManager.getRequestDeployState(request.getId());

    if (!requestDeployState.isPresent() || !requestDeployState.get().getActiveDeploy().isPresent()) {
      LOG.debug("No active deploy for {} - not scheduling on startup", request.getId());
      return;
    }

    requestManager.addToPendingQueue(new SingularityPendingRequest(request.getId(), requestDeployState.get().getActiveDeploy().get().getDeployId(), PendingType.STARTUP));
  }

  private void enqueueHealthAndNewTaskChecks() {
    final long start = System.currentTimeMillis();

    final List<SingularityTask> activeTasks = taskManager.getActiveTasks();
    final Map<SingularityTaskId, SingularityTask> activeTaskMap = Maps.uniqueIndex(activeTasks, taskTranscoder);

    final Map<SingularityTaskId, List<SingularityTaskHistoryUpdate>> taskUpdates = taskManager.getTaskHistoryUpdates(activeTaskMap.keySet());

    final Map<SingularityDeployKey, SingularityPendingDeploy> pendingDeploys = Maps.uniqueIndex(deployManager.getPendingDeploys(), SingularityDeployKey.FROM_PENDING_TO_DEPLOY_KEY);

    int enqueuedNewTaskChecks = 0;
    int enqueuedHealthchecks = 0;

    for (Map.Entry<SingularityTaskId, SingularityTask> entry: activeTaskMap.entrySet()) {
      SingularityTaskId taskId = entry.getKey();
      SingularityTask task = entry.getValue();
      SimplifiedTaskState simplifiedTaskState = SingularityTaskHistoryUpdate.getCurrentState(taskUpdates.get(taskId));

      if (simplifiedTaskState != SimplifiedTaskState.DONE) {
        SingularityDeployKey deployKey = new SingularityDeployKey(taskId.getRequestId(), taskId.getDeployId());
        Optional<SingularityPendingDeploy> pendingDeploy = Optional.fromNullable(pendingDeploys.get(deployKey));

        if (!pendingDeploy.isPresent()) {
          newTaskChecker.enqueueNewTaskCheck(task);
          enqueuedNewTaskChecks++;
        }
        if (simplifiedTaskState == SimplifiedTaskState.RUNNING) {
          if (healthchecker.enqueueHealthcheck(task, pendingDeploy)) {
            enqueuedHealthchecks++;
          }
        }
      }
    }

    LOG.info("Enqueued {} health checks and {} new task checks (out of {} active tasks) in {}", enqueuedHealthchecks, enqueuedNewTaskChecks, activeTasks.size(), JavaUtils.duration(start));
  }
}
