package com.hubspot.singularity.executor.cleanup;

public class SingularityExecutorCleanupStatistics {

  private final int totalTaskFiles;
  private final int ioErrorTasks;
  private final int runningTasksIgnored;
  private final int mesosRunningTasks;
  private final int staleTasks;
  private final int successfullyCleanedTasks;
  private final int errorTasks;
  private final int invalidTasks; 
  
  public SingularityExecutorCleanupStatistics(int totalTaskFiles, int mesosRunningTasks, int runningTasksIgnored, int staleTasks, int successfullyCleanedTasks, int ioErrorTasks, int errorTasks, int invalidTasks) {
    this.totalTaskFiles = totalTaskFiles;
    this.mesosRunningTasks = mesosRunningTasks;
    this.runningTasksIgnored = runningTasksIgnored;
    this.staleTasks = staleTasks;
    this.ioErrorTasks = ioErrorTasks;
    this.successfullyCleanedTasks = successfullyCleanedTasks;
    this.errorTasks = errorTasks;
    this.invalidTasks = invalidTasks;
  }
  
  public int getRunningTasksIgnored() {
    return runningTasksIgnored;
  }

  public int getIoErrorTasks() {
    return ioErrorTasks;
  }

  public int getTotalTaskFiles() {
    return totalTaskFiles;
  }

  public int getMesosRunningTasks() {
    return mesosRunningTasks;
  }

  public int getStaleTasks() {
    return staleTasks;
  }

  public int getSuccessfullyCleanedTasks() {
    return successfullyCleanedTasks;
  }

  public int getErrorTasks() {
    return errorTasks;
  }

  public int getInvalidTasks() {
    return invalidTasks;
  }

  @Override
  public String toString() {
    return "SingularityExecutorCleanupStatistics [totalTaskFiles=" + totalTaskFiles + ", ioErrorTasks=" + ioErrorTasks + ", runningTasksIgnored=" + runningTasksIgnored + ", mesosRunningTasks=" + mesosRunningTasks + ", staleTasks=" + staleTasks
        + ", successfullyCleanedTasks=" + successfullyCleanedTasks + ", errorTasks=" + errorTasks + ", invalidTasks=" + invalidTasks + "]";
  }

  public static class SingularityExecutorCleanupStatisticsBuilder {
    
    private int totalTaskFiles;
    private int runningTasksIgnored;
    private int mesosRunningTasks;
    private int staleTasks;
    private int successfullyCleanedTasks;
    private int ioErrorTasks;
    private int errorTasks;
    private int invalidTasks; 
    
    public void incrTotalTaskFiles() {
      totalTaskFiles++;
    }
    
    public void incrRunningTasksIgnored() {
      runningTasksIgnored++;
    }
    
    public void incrIoErrorTasks() {
      ioErrorTasks++;
    }
    
    public void setMesosRunningTasks(int mesosRunningTasks) {
      this.mesosRunningTasks = mesosRunningTasks;
    }
    
    public void incrStaleTasks() {
      staleTasks++;
    }
    
    public void incrErrorTasks() {
      errorTasks++;
    }
    
    public void incrSuccessfullyCleanedTasks() {
      successfullyCleanedTasks++;
    }
    
    public void incrInvalidTasks() {
      invalidTasks++;
    }
      
    public SingularityExecutorCleanupStatistics build() {
      return new SingularityExecutorCleanupStatistics(totalTaskFiles, mesosRunningTasks, runningTasksIgnored, staleTasks, successfullyCleanedTasks, ioErrorTasks, errorTasks, invalidTasks);
    }
    
  }
  
}
