package com.blue333.ordermgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables the Phase 4 background jobs: the daily overdue-exception scan, the nightly orphan sweep and
 * the SSE heartbeat. A dedicated pool keeps the heartbeat from starving the sweep jobs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
  @Bean
  public ThreadPoolTaskScheduler backgroundTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(3);
    scheduler.setThreadNamePrefix("ordermgmt-scheduler-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(20);
    return scheduler;
  }
}
