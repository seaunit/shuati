package com.shuati.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  @Bean("importExecutor")
  public Executor importExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // 解析任务全局串行：线程池只有 1 个线程，省 CPU / 内存 / AI 并发额度
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("import-");
    executor.initialize();
    return executor;
  }

  @Bean("emailExecutor")
  public Executor emailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("email-code-");
    executor.initialize();
    return executor;
  }
}
