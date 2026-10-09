package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuati.billing.PointAccountService;
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
class AiExplanationBillingTest {

  @Autowired
  AiGradingService aiGradingService;

  @Autowired
  PointAccountService points;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void cachedExplanationStillChargesAndWritesLedger() {
    String userId = createUser();
    points.ensureAccount(userId);
    long questionId = createCachedQuestion();

    int before = intValue(points.entitlements(userId).get("available"));
    Map<String, Object> response = aiGradingService.explanation(userId, questionId);

    assertThat(response.get("explanation")).isEqualTo("已缓存的标准解析");
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(before - 1);

    Integer ledgerCount = jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'AI_EXPLAIN' and ref_type = 'question' and ref_id = ?
        """, Integer.class, userId, String.valueOf(questionId));
    assertThat(ledgerCount).isEqualTo(1);
  }

  private long createCachedQuestion() {
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
