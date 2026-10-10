package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析库：题目接口要把已存好的解析一并带出来，刷题时无需再点「AI 解析」。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BankQuestionExplanationTest {

  @Autowired BankService bankService;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void unitQuestionsCarryStoredExplanation() {
    String userId = createUser();
    authenticate(userId);
    Long unitId = createUnit();
    long withExplanation = createQuestion(unitId, "解析库里的解析");
    long withoutExplanation = createQuestion(unitId, null);

    List<Map<String, Object>> questions = bankService.listUnitQuestions(unitId, "seq");
    Map<Long, Map<String, Object>> byId = questions.stream().collect(Collectors.toMap(
        q -> ((Number) q.get("id")).longValue(), Function.identity()));

    assertThat(byId.get(withExplanation).get("explanation")).isEqualTo("解析库里的解析");
    assertThat(byId.get(withoutExplanation).get("explanation")).isNull();
  }

  private void authenticate(String userId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "bank-explain-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private Long createUnit() {
    String bankName = "解析库测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '解析库单元', 1)", bankId);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? order by id desc limit 1", Long.class, bankId);
  }

  private long createQuestion(Long unitId, String explanation) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, explanation, status)
        values (?, 'SINGLE', ?, cast('[{"key":"A","text":"选项A"}]' as json),
                'A', cast('[]' as json), 'EASY', ?, 'ON')
        """, unitId, "解析库题干-" + UUID.randomUUID(), explanation);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }
}
