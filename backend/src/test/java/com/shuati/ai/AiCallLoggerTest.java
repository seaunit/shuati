package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AiCallLoggerTest {

  @Autowired AiCallLogger logger;
  @Autowired JdbcTemplate jdbc;

  @Test
  void successRecordsDifyWorkflowIdentityAndUsage() {
    String userId = createUser();
    AiWorkflowResult result = new AiWorkflowResult(
        "{}", "DIFY", AiWorkflowKey.JUDGE, "run-success",
        "workflow:judge", 11, 7, 18, 123);

    logger.success(userId, "JUDGE", 42L, result);

    Map<String, Object> row = jdbc.queryForMap("""
        select provider, workflow_key, external_run_id, model,
               prompt_tokens, completion_tokens, total_tokens, success,
               error, duration_ms
          from ai_call_log
         where user_id = ? and purpose = 'JUDGE'
         order by id desc limit 1
        """, userId);
    assertThat(row.get("provider")).isEqualTo("DIFY");
    assertThat(row.get("workflow_key")).isEqualTo("judge");
    assertThat(row.get("external_run_id")).isEqualTo("run-success");
    assertThat(row.get("model")).isEqualTo("workflow:judge");
    assertThat(((Number) row.get("total_tokens")).intValue()).isEqualTo(18);
    assertThat(row.get("success")).isEqualTo(true);
    assertThat(((Number) row.get("duration_ms")).intValue()).isEqualTo(123);
  }

  @Test
  void failureRedactsBearerAndProviderKeys() {
    String userId = createUser();

    logger.failure(
        userId, "EXPLAIN", null, "DIFY", AiWorkflowKey.EXPLAIN, "run-failed",
        "Bearer app-super-secret failed with sk-provider-secret", 250);

    String error = jdbc.queryForObject("""
        select error from ai_call_log
         where user_id = ? and purpose = 'EXPLAIN'
         order by id desc limit 1
        """, String.class, userId);
    assertThat(error)
        .doesNotContain("app-super-secret")
        .doesNotContain("sk-provider-secret")
        .contains("***");
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "ai-log-" + id.substring(0, 8) + "@example.com");
    return id;
  }
}
