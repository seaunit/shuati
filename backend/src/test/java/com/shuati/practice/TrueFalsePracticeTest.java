package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TrueFalsePracticeTest {

  @Autowired PracticeService practiceService;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void trueFalseQuestionsUseLocalGradingAndWrongBookRules() {
    String userId = createUser();
    authenticate(userId);
    long questionId = createTrueFalseQuestion("T");

    Map<String, Object> correct = practiceService.submitChoice(questionId, List.of("T"), 1200);
    assertThat(correct.get("verdict")).isEqualTo("CORRECT");
    assertThat(correct.get("score")).isEqualTo(10);
    assertThat(correct.get("correctAnswer")).isEqualTo("T");
    assertThat(practiceService.wrongBook()).isEmpty();

    Map<String, Object> wrong = practiceService.submitChoice(questionId, List.of("F"), 900);
    assertThat(wrong.get("verdict")).isEqualTo("WRONG");
    assertThat(wrong.get("score")).isEqualTo(0);
    assertThat(practiceService.wrongBook()).hasSize(1);
    assertThat(practiceService.wrongBook().get(0).get("type")).isEqualTo("TRUE_FALSE");

    Integer aiCalls = jdbc.queryForObject(
        "select count(*) from ai_call_log where question_id = ?", Integer.class, questionId);
    assertThat(aiCalls).isZero();
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
        """, id, "truefalse-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createTrueFalseQuestion(String answer) {
    String bankName = "判断题测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '测试单元', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ? order by id desc limit 1", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'TRUE_FALSE', 'Spring Bean 默认是单例。',
                cast('[{"key":"T","text":"正确"},{"key":"F","text":"错误"}]' as json),
                ?, null, 'EASY', 'ON')
        """, unitId, answer);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }
}
