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

/**
 * 错题本的两条固定行为（安卓端与网页端共用同一份实现）。
 *
 * <p>1. 同一道题反复答错，错题本里只保留一条（按题目去重，展示最近一次作答）；
 * 2. 最近一次答对了，这道题就从错题本消失（练习记录仍然保留，便于统计与复盘）。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class WrongBookDedupTest {

  @Autowired PracticeService practiceService;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void answeringTheSameQuestionWrongTwiceKeepsOnlyOneRow() {
    String userId = createUser();
    authenticate(userId);
    long questionId = createChoiceQuestion();

    practiceService.submitChoice(questionId, List.of("B"), 1000);
    practiceService.submitChoice(questionId, List.of("C"), 900);

    List<Map<String, Object>> wrong = practiceService.wrongBook();
    assertThat(wrong).hasSize(1);
    assertThat(wrong.get(0).get("question_id")).isEqualTo(questionId);
    assertThat(wrong.get(0).get("verdict")).isEqualTo("WRONG");

    // 记录本身是两条（复盘要用），只是错题本按题目收敛成一条
    Integer records = jdbc.queryForObject(
        "select count(*) from practice_record where user_id = ? and question_id = ?",
        Integer.class, userId, questionId);
    assertThat(records).isEqualTo(2);
  }

  @Test
  void answeringItCorrectlyRemovesItFromTheWrongBook() {
    String userId = createUser();
    authenticate(userId);
    long questionId = createChoiceQuestion();

    practiceService.submitChoice(questionId, List.of("B"), 1000);
    assertThat(practiceService.wrongBook()).hasSize(1);

    practiceService.submitChoice(questionId, List.of("A"), 800);
    assertThat(practiceService.wrongBook()).isEmpty();
  }

  @Test
  void otherUsersWrongQuestionsAreNotVisible() {
    String owner = createUser();
    authenticate(owner);
    long questionId = createChoiceQuestion();
    practiceService.submitChoice(questionId, List.of("B"), 1000);

    authenticate(createUser());
    assertThat(practiceService.wrongBook()).isEmpty();
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
        """, id, "wrongbook-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createChoiceQuestion() {
    String bankName = "错题本测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '测试单元', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ? order by id desc limit 1", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', '测试题干',
                cast('[{"key":"A","text":"选项A"},{"key":"B","text":"选项B"},{"key":"C","text":"选项C"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }
}
