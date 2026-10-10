package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析任务全局串行：已有任务在跑时，新任务必须被拒绝。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ImportSingleTaskTest {

  @Autowired ImportService importService;
  @Autowired JdbcTemplate jdbc;

  @Test
  void createTaskIsRejectedWhileAnotherTaskIsStillRunning() {
    // 先清掉库里可能残留的进行中任务（事务内，测试结束回滚）
    jdbc.update("update import_task set status = 'FAILED' where status in ('PENDING', 'RUNNING')");
    jdbc.update("""
        insert into import_task
          (id, user_id, kind, phase, status, bank_name, source_text)
        values (?, ?, 'text', 'PARSE', 'RUNNING', '进行中的任务', '源文本')
        """, UUID.randomUUID().toString(), createUser());

    ApiException error = assertThrows(ApiException.class, () -> importService.createTask(
        createUser(), Map.of("kind", "text", "bankName", "并发测试", "text", "正文")));
    assertThat(error.getStatus()).isEqualTo(409);
    assertThat(error.getMessage()).contains("已有解析任务正在进行中");

    // 当前任务结束（完成或失败）后即可提交下一个
    jdbc.update("update import_task set status = 'FAILED' where status = 'RUNNING'");
    Map<String, Object> created = importService.createTask(
        createUser(), Map.of("kind", "text", "bankName", "并发测试", "text", "正文"));
    assertThat(created.get("taskId")).isNotNull();
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "import-single-" + id.substring(0, 8) + "@example.com");
    return id;
  }
}
