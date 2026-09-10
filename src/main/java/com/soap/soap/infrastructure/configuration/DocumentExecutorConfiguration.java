package com.soap.soap.infrastructure.configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DocumentExecutorConfiguration {
  @Bean("documentImportExecutor")
  Executor documentImportExecutor(
      @Value("${app.documents.executor-core-size:2}") int coreSize,
      @Value("${app.documents.executor-max-size:4}") int maxSize,
      @Value("${app.documents.executor-queue-capacity:20}") int queueCapacity) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(coreSize);
    executor.setMaxPoolSize(maxSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("document-import-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }
}
