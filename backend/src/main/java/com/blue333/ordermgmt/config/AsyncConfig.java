package com.blue333.ordermgmt.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
  @Bean(name = "auditLogExecutor")
  Executor auditLogExecutor() {
    return createExecutor("audit-log-");
  }

  @Bean(name = "notificationExecutor")
  Executor notificationExecutor() {
    return createExecutor("notification-");
  }

  private Executor createExecutor(String threadNamePrefix) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
  }
}
