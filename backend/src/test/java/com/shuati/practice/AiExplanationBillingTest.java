package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.shuati.ai.AiClient;
import com.shuati.billing.PointAccountService;
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
class AiExplanationBillingTest {

  @Autowired
  AiGradingService aiGradingService;

  @Autowired
  PointAccountService points;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  AiClient ai;

  @Test
  void clickingExplainAgainRegeneratesAndOverwritesLibraryEntry() {
    when(ai.chat(anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(new AiClient.AiResult("重新生成的解析内容", "test-model", 10, 20, 30));

    String userId = createUser();
    points.ensureAccount(userId);
    long questionId = createQuestionWithStoredExplanation();

    int before = intValue(points.entitlements(userId).get("available"));
    Map<String, Object> response = aiGradingService.explanation(userId, questionId);

    // 再次点击「AI 解析」＝重新生成，不能被解析库里的旧内容挡住
    assertThat(response.get("explanation")).isEqualTo("重新生成的解析内容");
    assertThat(jdbc.queryForObject(
        "select explanation from question where id = ?", String.class, questionId))
        .isEqualTo("重新生成的解析内容");

    // 仍然按次扣点并写流水
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(before - 1);
    Integer ledgerCount = jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_EXPLAIN' and ref_type = 'question' and ref_id = ?
        """, Integer.class, userId, String.valueOf(questionId));
    assertThat(ledgerCount).isEqualTo(1);
  }

  private long createQuestionWithStoredExplanation() {
    String bankName = "AI解析计费测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '测试单元', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ? order by id desc limit 1", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, explanation, status)
        values (?, 'SINGLE', '测试题干', cast('[{"key":"A","text":"选项A"}]' as json),
                'A', cast('[]' as json), 'EASY', '已缓存的标准解析', 'ON')
        """, unitId);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "ai-explain-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }
}
