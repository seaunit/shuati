package com.shuati.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class AdminTrueFalseQuestionTest {

  @Autowired AdminContentController controller;
  @Autowired JdbcTemplate jdbc;

  @Test
  void adminCreateAndUpdateCanonicalTrueFalseQuestion() {
    String bankName = "后台判断题-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    long bankId = jdbc.queryForObject(
        "select id from bank where name = ?", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '判断题', 1)", bankId);
    long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ?", Long.class, bankId);

    controller.createQuestion(Map.of(
        "unitId", unitId,
        "type", "TRUE_FALSE",
        "content", "Spring Bean 默认是单例。",
        "answer", "正确",
        "options", List.of(Map.of("key", "A", "text", "错误"))));

    long questionId = jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1",
        Long.class, unitId);
    Map<String, Object> created = jdbc.queryForMap(
        "select type, cast(options as char) options, answer from question where id = ?",
        questionId);
    assertThat(created.get("type")).isEqualTo("TRUE_FALSE");
    assertThat(created.get("answer")).isEqualTo("T");
    assertThat(String.valueOf(created.get("options")))
        .contains("\"key\": \"T\"")
        .contains("\"key\": \"F\"");

    controller.updateQuestion(questionId, Map.of(
        "type", "TRUE_FALSE",
        "content", "Spring Bean 默认是原型。",
        "answer", "×"));
    Map<String, Object> updated = jdbc.queryForMap(
        "select content, answer from question where id = ?", questionId);
    assertThat(updated.get("content")).isEqualTo("Spring Bean 默认是原型。");
    assertThat(updated.get("answer")).isEqualTo("F");
  }
}
