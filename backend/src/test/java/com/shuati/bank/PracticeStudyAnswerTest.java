package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
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
class PracticeStudyAnswerTest {

  @Autowired BankService bankService;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ownerCanReadAnswerForStudyMode() {
    String ownerId = createUser();
    long bankId = createBank(ownerId);
    long questionId = createQuestion(createUnit(bankId), "A", "ON");
    authenticate(ownerId);

    Map<String, Object> result = bankService.questionAnswer(questionId);

    assertThat(result.get("answer")).isEqualTo("A");
  }

  @Test
  void otherUsersCannotReadPrivateBankAnswer() {
    String ownerId = createUser();
    long bankId = createBank(ownerId);
    long questionId = createQuestion(createUnit(bankId), "B", "ON");
    authenticate(createUser());

    ApiException error = assertThrows(
        ApiException.class, () -> bankService.questionAnswer(questionId));

    assertThat(error.getStatus()).isEqualTo(403);
  }

  @Test
  void offlineQuestionDoesNotRevealAnswer() {
    String ownerId = createUser();
    long bankId = createBank(ownerId);
    long questionId = createQuestion(createUnit(bankId), "C", "OFF");
    authenticate(ownerId);

    ApiException error = assertThrows(
        ApiException.class, () -> bankService.questionAnswer(questionId));

    assertThat(error.getStatus()).isEqualTo(404);
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
        """, id, "study-answer-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createBank(String ownerId) {
    String name = "背题答案测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, name);
  }

  private long createUnit(long bankId) {
    String name = "单元-" + UUID.randomUUID();
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 1)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private long createQuestion(long unitId, String answer, String status) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', ?, cast('[{"key":"A","text":"A"},{"key":"B","text":"B"}]' as json),
                ?, null, 'EASY', ?)
        """, unitId, "背题答案题干-" + UUID.randomUUID(), answer, status);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }
}
