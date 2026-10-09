package com.shuati.bank;

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
class BankProgressTest {

  @Autowired BankService bankService;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allProgressExposesAnsweredQuestionIdsSoPracticeCanResume() {
    String userId = createUser();
    long answeredQuestionId = createQuestion(false);
    long untouchedQuestionId = createQuestion(true);
    jdbc.update("""
        insert into practice_record
          (user_id, question_id, user_answer, judge_source, verdict, score)
        values (?, ?, 'A', 'LOCAL', 'CORRECT', 10)
        """, userId, answeredQuestionId);
    authenticate(userId);

    Map<String, Object> progress = bankService.allProgress();

    @SuppressWarnings("unchecked")
    List<Number> answeredIds = (List<Number>) progress.get("answeredIds");
    List<Long> ids = answeredIds.stream().map(Number::longValue).toList();
    assertThat(ids).contains(answeredQuestionId);
    assertThat(ids).doesNotContain(untouchedQuestionId);
    assertThat(((Number) progress.get("answered")).longValue()).isGreaterThanOrEqualTo(1);
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
        """, id, "bank-progress-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createQuestion(boolean even) {
    String bankName = "进度测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '进度单元', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ? order by id desc limit 1", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', ?, cast('[{"key":"A","text":"选项A"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId, "进度测试题干-" + (even ? "even" : "odd") + "-" + UUID.randomUUID());
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }
}
