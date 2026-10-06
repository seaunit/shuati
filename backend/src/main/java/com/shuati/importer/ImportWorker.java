package com.shuati.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportWorker {

  private final ImportService importService;

  @Async("importExecutor")
  public void run(String taskId) {
    importService.runTask(taskId);
  }
}
