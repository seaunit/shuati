package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.ai.AiClient;
import com.shuati.ai.AiWorkflowInputs;
import com.shuati.ai.AiWorkflowKey;
import com.shuati.ai.AiWorkflowPort;
import com.shuati.ai.AiWorkflowResult;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import java.util.Map;
import java.util.UUID;
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
class AiGradingServiceDifyTest {

  @Autowired AiGradingService service;
  @Autowired PointAccountService points;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper objectMapper;

  @MockitoBean AiWorkflowPort aiWorkflow;
  @MockitoBean AiClient legacyAi;

  @Test
  void successfulDifyJudgeReturnsExistingContractAndLogsWorkflow() throws Exception {
    String userId = createUser();
    points.ensureAccount(userId);
    long questionId = createShortQuestion("HashMap 扩容");
    String content = objectMapper.writeValueAsString(Map.of(
        "verdict", "CORRECT",
        "score", 9,
        "hit_points", java.util.List.of("扩容"),
        "missed_points", java.util.List.of(),
        "feedback", "回答正确"));
    when(aiWorkflow.judge(anyString(), any(AiWorkflowInputs.JudgeInput.class)))
        .thenReturn(new AiWorkflowResult(
            content, "DIFY", AiWorkflowKey.JUDGE, "run-judge",
            "dify:judge", 0, 0, 20, 140));

    Map<String, Object> response = service.submitEssay(userId, questionId, "我的回答", 1000);

    assertThat(response.get("verdict")).isEqualTo("CORRECT");
    assertThat(response.get("score")).isEqualTo(9);
    assertThat(response.get("degraded")).isEqualTo(false);
    Map<String, Object> log = jdbc.queryForMap("""
        select provider, workflow_key, external_run_id, success
          from ai_call_log
         where user_id = ? and purpose = 'JUDGE'
         order by id desc limit 1
        """, userId);
    assertThat(log.get("provider")).isEqualTo("DIFY");
    assertThat(log.get("workflow_key")).isEqualTo("judge");
    assertThat(log.get("external_run_id")).isEqualTo("run-judge");
    assertThat(log.get("success")).isEqualTo(true);
  }

  @Test
  void failedDifyJudgeRefundsPointsAndRecordsFailure() {
    String userId = createUser();
    points.ensureAccount(userId);
    long questionId = createShortQuestion("事务隔离");
    int before = intValue(points.entitlements(userId).get("available"));
    when(aiWorkflow.judge(anyString(), any(AiWorkflowInputs.JudgeInput.class)))
        .thenThrow(new ApiException(502, "AI 工作流执行失败"));

    Map<String, Object> response = service.submitEssay(userId, questionId, "我的回答", 900);

    assertThat(response.get("degraded")).isEqualTo(true);
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(before);
    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_JUDGE_FAILED'
        """, Integer.class, userId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
        select success from ai_call_log
         where user_id = ? and purpose = 'JUDGE'
         order by id desc limit 1
        """, Boolean.class, userId)).isFalse();
  }

  private long createShortQuestion(String content) {
    String bankName = "Dify判题-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ?", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '简答题', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ?", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, answer, key_points, difficulty, status)
        values (?, 'SHORT', ?, '参考答案', cast('["要点"]' as json), 'MEDIUM', 'ON')
        """, unitId, content);
    return jdbc.queryForObject(
        "select id from question where unit_id = ?", Long.class, unitId);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "dify-judge-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }
}
