package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.shuati.ai.AiClient;
import com.shuati.ai.AiWorkflowInputs;
import com.shuati.ai.AiWorkflowKey;
import com.shuati.ai.AiWorkflowPort;
import com.shuati.ai.AiWorkflowResult;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ImportDifyWorkflowTest {

  private static final String PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  @Autowired ImportService importService;
  @Autowired PointAccountService points;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean AiWorkflowPort aiWorkflow;
  @MockitoBean AiClient legacyAi;

  @BeforeEach
  void clearRunningTasks() {
    jdbc.update("update import_task set status = 'FAILED' where status in ('PENDING', 'RUNNING')");
  }

  @Test
  void textTaskUsesDifyExtractionAndStoresQuestions() {
    String userId = createUser();
    when(aiWorkflow.extractText(anyString(), any(AiWorkflowInputs.ExtractTextInput.class)))
        .thenReturn(new AiWorkflowResult(
            "{\"questions\":[{\"content\":\"Dify 生成的题目\",\"type\":\"SINGLE\","
                + "\"options\":[{\"key\":\"A\",\"text\":\"选项\"}],\"answer\":\"A\"}]}",
            "DIFY", AiWorkflowKey.EXTRACT_TEXT, "run-text",
            "dify:extract-text", 0, 0, 10, 100));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", "text");
    body.put("bankName", "Dify导入");
    body.put("text", "一条笔记正文");
    String taskId = String.valueOf(importService.createTask(userId, body).get("taskId"));

    importService.runTask(taskId);

    assertThat(jdbc.queryForObject(
        "select status from import_task where id = ?", String.class, taskId))
        .isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject(
        "select cast(items as char) from import_task where id = ?", String.class, taskId))
        .contains("Dify 生成的题目");
    assertThat(jdbc.queryForObject("""
        select provider from ai_call_log
         where user_id = ? and purpose = 'EXTRACT'
         order by id desc limit 1
        """, String.class, userId)).isEqualTo("DIFY");
  }

  @Test
  void screenshotTaskUsesDifyImageWorkflow() {
    String userId = createUser();
    when(aiWorkflow.extractScreenshot(
        anyString(), any(AiWorkflowInputs.ExtractScreenshotInput.class)))
        .thenReturn(new AiWorkflowResult(
            "{\"questions\":[{\"content\":\"截图题目\",\"type\":\"SINGLE\","
                + "\"options\":[{\"key\":\"A\",\"text\":\"选项\"}],\"answer\":\"A\"}]}",
            "DIFY", AiWorkflowKey.EXTRACT_IMAGE, "run-image",
            "dify:extract-image", 0, 0, 20, 200));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", "screenshot");
    body.put("bankName", "Dify截图");
    body.put("base64", PNG);
    body.put("fileName", "screenshot.png");
    String taskId = String.valueOf(importService.createTask(userId, body).get("taskId"));

    importService.runTask(taskId);

    assertThat(jdbc.queryForObject(
        "select status from import_task where id = ?", String.class, taskId))
        .isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject(
        "select cast(items as char) from import_task where id = ?", String.class, taskId))
        .contains("截图题目");
  }

  @Test
  void screenshotTaskAcceptsMarkdownFencedJsonFromDify() {
    String userId = createUser();
    when(aiWorkflow.extractScreenshot(
        anyString(), any(AiWorkflowInputs.ExtractScreenshotInput.class)))
        .thenReturn(new AiWorkflowResult(
            "```json\n"
                + "{\"questions\":[{\"content\":\"围栏中的截图题目\",\"type\":\"SHORT\","
                + "\"answer\":\"参考答案\"}]}\n"
                + "```",
            "DIFY", AiWorkflowKey.EXTRACT_IMAGE, "run-image-fenced",
            "dify:extract-image", 0, 0, 20, 200));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", "screenshot");
    body.put("bankName", "Dify围栏截图");
    body.put("base64", PNG);
    body.put("fileName", "screenshot.png");
    String taskId = String.valueOf(importService.createTask(userId, body).get("taskId"));

    importService.runTask(taskId);

    assertThat(jdbc.queryForObject(
        "select status from import_task where id = ?", String.class, taskId))
        .isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject(
        "select cast(items as char) from import_task where id = ?", String.class, taskId))
        .contains("围栏中的截图题目");
  }

  @Test
  void invalidDifyQuestionJsonFailsAndRefundsImportCharge() {
    String userId = createUser();
    when(aiWorkflow.extractText(anyString(), any(AiWorkflowInputs.ExtractTextInput.class)))
        .thenThrow(new ApiException(502, "AI 工作流返回格式不正确"));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", "text");
    body.put("bankName", "Dify失败");
    body.put("text", "失败正文");
    String taskId = String.valueOf(importService.createTask(userId, body).get("taskId"));
    int before = intValue(points.entitlements(userId).get("available"));

    importService.runTask(taskId);

    assertThat(jdbc.queryForObject(
        "select status from import_task where id = ?", String.class, taskId))
        .isEqualTo("FAILED");
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(before);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "dify-import-" + id.substring(0, 8) + "@example.com");
    points.ensureAccount(id);
    return id;
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }
}
