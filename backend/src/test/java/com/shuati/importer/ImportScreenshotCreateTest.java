package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 复现线上「截图导入一提交就 500」：createTask 是同步执行的，异常会直接冒出来 */
@ActiveProfiles("test")
@SpringBootTest
class ImportScreenshotCreateTest {

  // 1x1 透明 PNG
  private static final String PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  @Autowired
  ImportService importService;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void screenshotTaskCanBeCreated() {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, role, status)
        values (?, ?, 'USER', 'ENABLED')
        """, userId, "shot-" + userId + "@example.com");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", "screenshot");
    body.put("base64", PNG);
    body.put("fileName", "screenshot.png");
    body.put("bankName", "截图测试");

    Map<String, Object> created = importService.createTask(userId, body);
    String taskId = String.valueOf(created.get("taskId"));

    assertThat(taskId).isNotBlank();
    assertThat(jdbc.queryForObject(
        "select kind from import_task where id = ?", String.class, taskId))
        .isEqualTo("screenshot");
  }
}
