package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shuati.ai.AiClient;
import com.shuati.ai.AiWorkflowInputs;
import com.shuati.ai.AiWorkflowKey;
import com.shuati.ai.AiWorkflowPort;
import com.shuati.ai.AiWorkflowResult;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AskAiServiceTest {

  @Autowired AskAiService service;
  @Autowired PointAccountService points;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean AiWorkflowPort aiWorkflow;
  @MockitoBean AiClient legacyAi;

  @BeforeEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void publicQuestionUsesServerContextAndChargesOnePoint() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    long questionId = createQuestion(null);
    int before = available(userId);
    when(aiWorkflow.ask(eq(userId), any(AiWorkflowInputs.AskInput.class)))
        .thenReturn(new AiWorkflowResult(
            "**回答**：半同步复制至少等待一个从库确认。",
            "DIFY",
            AiWorkflowKey.ASK,
            "run-ask",
            "dify:ask",
            0,
            0,
            20,
            120));

    Map<String, Object> response = service.ask(
        userId,
        questionId,
        "半同步复制有什么作用？",
        List.of(Map.of("role", "user", "content", "先解释主从延迟")),
        UUID.randomUUID().toString());

    assertThat(response.get("answer")).isEqualTo("**回答**：半同步复制至少等待一个从库确认。");
    assertThat(response.get("cost")).isEqualTo(1);
    assertThat(available(userId)).isEqualTo(before - 1);

    ArgumentCaptor<AiWorkflowInputs.AskInput> input =
        ArgumentCaptor.forClass(AiWorkflowInputs.AskInput.class);
    verify(aiWorkflow).ask(eq(userId), input.capture());
    assertThat(input.getValue().questionContent()).contains("主从延迟");
    assertThat(input.getValue().options()).contains("\"key\": \"A\"");
    assertThat(input.getValue().referenceAnswer()).isEqualTo("B");
    assertThat(input.getValue().explanation()).contains("已有解析");
    assertThat(input.getValue().conversation()).contains("先解释主从延迟");
    assertThat(input.getValue().userQuestion()).isEqualTo("半同步复制有什么作用？");

    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_ASK'
        """, Integer.class, userId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
        select count(*) from ai_call_log
         where user_id = ? and purpose = 'ASK' and success = 1
        """, Integer.class, userId)).isEqualTo(1);
  }

  @Test
  void otherUsersPrivateQuestionIsRejectedBeforeAiOrCharge() {
    String ownerId = createUser();
    String actorId = createUser();
    loginAs(actorId);
    points.ensureAccount(actorId);
    long questionId = createQuestion(ownerId);
    int before = available(actorId);

    assertThatThrownBy(() -> service.ask(
        actorId,
        questionId,
        "这个知识点怎么扩展？",
        List.of(),
        UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));

    verify(aiWorkflow, never()).ask(anyString(), any(AiWorkflowInputs.AskInput.class));
    assertThat(available(actorId)).isEqualTo(before);
  }

  @Test
  void aiFailureRefundsPointsAndRecordsFailure() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    long questionId = createQuestion(null);
    int before = available(userId);
    String requestId = UUID.randomUUID().toString();
    when(aiWorkflow.ask(eq(userId), any(AiWorkflowInputs.AskInput.class)))
        .thenThrow(new ApiException(502, "AI 工作流执行失败"));

    assertThatThrownBy(() -> service.ask(
        userId,
        questionId,
        "为什么？",
        List.of(),
        requestId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("问 AI 失败");

    assertThat(available(userId)).isEqualTo(before);
    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_ASK_FAILED'
        """, Integer.class, userId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
        select count(*) from ai_call_log
         where user_id = ? and purpose = 'ASK' and success = 0
        """, Integer.class, userId)).isEqualTo(1);
  }

  @Test
  void duplicateRequestReturnsCachedAnswerWithoutSecondCallOrCharge() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    long questionId = createQuestion(null);
    int before = available(userId);
    String requestId = UUID.randomUUID().toString();
    when(aiWorkflow.ask(eq(userId), any(AiWorkflowInputs.AskInput.class)))
        .thenReturn(new AiWorkflowResult(
            "只生成一次的回答",
            "DIFY",
            AiWorkflowKey.ASK,
            "run-idempotent",
            "dify:ask",
            0,
            0,
            10,
            80));

    Map<String, Object> first = service.ask(
        userId, questionId, "重复问题", List.of(), requestId);
    Map<String, Object> second = service.ask(
        userId, questionId, "重复问题", List.of(), requestId);

    assertThat(first.get("answer")).isEqualTo("只生成一次的回答");
    assertThat(second.get("answer")).isEqualTo("只生成一次的回答");
    verify(aiWorkflow, times(1)).ask(eq(userId), any(AiWorkflowInputs.AskInput.class));
    assertThat(available(userId)).isEqualTo(before - 1);
    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_ASK'
        """, Integer.class, userId)).isEqualTo(1);
  }

  @Test
  void invalidQuestionIsRejectedBeforeAi() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    long questionId = createQuestion(null);

    assertThatThrownBy(() -> service.ask(
        userId, questionId, " ", List.of(), UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(400));

    verify(aiWorkflow, never()).ask(anyString(), any(AiWorkflowInputs.AskInput.class));
  }

  @Test
  void insufficientPointsDoesNotCallAi() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    jdbc.update("""
        update point_account
           set monthly_quota = 0, monthly_used = 0, bonus_balance = 0
         where user_id = ?
        """, userId);
    long questionId = createQuestion(null);

    assertThatThrownBy(() -> service.ask(
        userId, questionId, "余额不足怎么办？", List.of(), UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(402));

    verify(aiWorkflow, never()).ask(anyString(), any(AiWorkflowInputs.AskInput.class));
  }

  @Test
  void historyLongerThanTwentyMessagesIsRejected() {
    String userId = createUser();
    loginAs(userId);
    points.ensureAccount(userId);
    long questionId = createQuestion(null);
    List<Map<String, String>> history = java.util.stream.IntStream.range(0, 21)
        .mapToObj(index -> Map.of(
            "role", index % 2 == 0 ? "user" : "assistant",
            "content", "消息 " + index))
        .toList();

    assertThatThrownBy(() -> service.ask(
        userId, questionId, "历史太长", history, UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(400));

    verify(aiWorkflow, never()).ask(anyString(), any(AiWorkflowInputs.AskInput.class));
  }

  private long createQuestion(String ownerId) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    jdbc.update("""
        insert into bank (name, description, is_default, owner_id)
        values (?, '问 AI 测试题库', 0, ?)
        """, "问AI-" + suffix, ownerId);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ?", Long.class, "问AI-" + suffix);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, 'MySQL', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ?", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, explanation, difficulty, status)
        values (?, 'SINGLE',
          '主从延迟时首先应该检查什么？',
          cast('[{"key":"A","text":"Seconds_Behind_Master"},{"key":"B","text":"半同步复制"}]' as json),
          'B', '已有解析：先确认延迟来源和复制线程状态。', 'MEDIUM', 'ON')
        """, unitId);
    return jdbc.queryForObject(
        "select id from question where unit_id = ?", Long.class, unitId);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "ask-ai-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private int available(String userId) {
    return ((Number) points.entitlements(userId).get("available")).intValue();
  }

  private void loginAs(String userId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }
}
