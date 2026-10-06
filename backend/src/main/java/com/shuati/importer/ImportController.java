package com.shuati.importer;

import com.shuati.common.ApiResponse;
import com.shuati.common.CurrentUser;
import com.shuati.rate.RateLimit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImportController {

  private final ImportService importService;
  private final ImportWorker importWorker;

  @PostMapping("/api/import")
  // 导入会消耗 AI 点数：同一用户每小时最多 5 个任务
  @RateLimit(name = "import:create", limit = 5, windowSeconds = 3600, scope = RateLimit.Scope.USER)
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    Map<String, Object> result = importService.createTask(CurrentUser.id(), body);
    importWorker.run(String.valueOf(result.get("taskId")));
    return ApiResponse.ok(result);
  }

  @GetMapping("/api/import/list")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(importService.listTasks(CurrentUser.id()));
  }

  @GetMapping("/api/import/task/{id}")
  public ApiResponse<Map<String, Object>> task(@PathVariable String id) {
    return ApiResponse.ok(importService.getTask(CurrentUser.id(), id));
  }

  @PostMapping("/api/import/task/{id}/import")
  public ApiResponse<Void> requestImport(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Integer> selected = body.get("selectedIndexes") instanceof List<?> list
        ? list.stream().map(v -> ((Number) v).intValue()).toList()
        : List.of();
    importService.requestImport(CurrentUser.id(), id, selected);
    importWorker.run(id);
    return ApiResponse.ok(null);
  }
}
