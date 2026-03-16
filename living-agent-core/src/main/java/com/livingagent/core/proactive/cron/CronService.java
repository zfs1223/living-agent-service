package com.livingagent.core.proactive.cron;

import java.util.List;
import java.util.Optional;

public interface CronService {

    CronJob scheduleJob(CronJob job);
    
    boolean unscheduleJob(String jobId);
    
    Optional<CronJob> getJob(String jobId);
    
    List<CronJob> getAllJobs();
    
    List<CronJob> getEnabledJobs();
    
    boolean enableJob(String jobId);
    
    boolean disableJob(String jobId);
    
    CronJob updateJob(CronJob job);
    
    void executeJob(String jobId);
    
    void start();
    
    void stop();
    
    boolean isRunning();
    
    long getNextExecutionTime(String jobId);
}
