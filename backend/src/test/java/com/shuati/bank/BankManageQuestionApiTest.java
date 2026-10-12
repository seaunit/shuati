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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 手工录入题目（题库管理里的「新增题目」「编辑题目」）。
 *
 * <p>这组接口和 /api/admin/questions 是同一套校验，区别只在权限：谁有题库的维护权，
 * 谁就能往里面加题改题，所以权限用例和校验用例都要覆盖。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BankManageQuestionApiTest {

  @Autowired BankManageController controller;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ownerCanCreateThenEditOwnQuestion() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);

    long questionId = createQuestion(Map.of(
        "unitId", unitId,
        "type", "SINGLE",
        "content", "下面哪个是索引的作用？",
        "options", List.of(
            Map.of("key", "A", "text", "加快查询"),
            Map.of("key", "B", "text", "加快写入")),
        "answer", "A",
        "difficulty", "HARD",
        "explanation", "索引用来加速检索",
        "tags", List.of("数据库")));

    Map<String, Object> row = jdbc.queryForMap(
        "select type, content, options, answer, difficulty, explanation, tags, status"
            + " from question where id = ?", questionId);
    assertThat(row.get("type")).isEqualTo("SINGLE");
    assertThat(row.get("content")).isEqualTo("下面哪个是索引的作用？");
    assertThat(String.valueOf(row.get("options"))).contains("加快查询");
    assertThat(row.get("answer")).isEqualTo("A");
    assertThat(row.get("difficulty")).isEqualTo("HARD");
    assertThat(row.get("explanation")).isEqualTo("索引用来加速检索");
    assertThat(String.valueOf(row.get("tags"))).contains("数据库");
    // 手工录入的题目默认就是上架的
    assertThat(row.get("status")).isEqualTo("ON");

    controller.updateQuestion(questionId, Map.of(
        "type", "MULTI",
        "content", "下面哪些属于索引的代价？",
        "options", List.of(
            Map.of("key", "A", "text", "占空间"),
            Map.of("key", "B", "text", "拖慢写入")),
        "answer", "AB",
        "difficulty", "EASY"));

    Map<String, Object> updated = jdbc.queryForMap(
        "select type, content, answer, difficulty from question where id = ?", questionId);
    assertThat(updated.get("type")).isEqualTo("MULTI");
    assertThat(updated.get("content")).isEqualTo("下面哪些属于索引的代价？");
    assertThat(updated.get("answer")).isEqualTo("AB");
    assertThat(updated.get("difficulty")).isEqualTo("EASY");
  }

  @Test
  void shortQuestionNeedsReferenceAnswerAndKeyPoints() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "我的题库"), "第一章");
    authenticate(userId, false);

    ApiException noKeyPoints = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId, "type", "SHORT", "content", "说说索引的作用", "answer", "参考答案")));
    assertThat(noKeyPoints.getStatus()).isEqualTo(400);
    assertThat(noKeyPoints.getMessage()).contains("评分要点");

    long questionId = createQuestion(Map.of(
        "unitId", unitId,
        "type", "SHORT",
        "content", "说说索引的作用",
        "answer", "参考答案",
        "keyPoints", List.of("说明加速检索", "说明写入代价")));

    assertThat(jdbc.queryForObject(
        "select cast(key_points as char) from question where id = ?", String.class, questionId))
        .contains("说明加速检索");
  }

  @Test
  void badChoiceQuestionIsRefused() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "我的题库"), "第一章");
    authenticate(userId, false);

    // 选择题不给选项
    ApiException noOptions = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId, "type", "SINGLE", "content", "题干", "answer", "A")));
    assertThat(noOptions.getStatus()).isEqualTo(400);
    assertThat(noOptions.getMessage()).contains("选项");

    // 单选题答案给了两个
    ApiException multiAnswer = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId,
        "type", "SINGLE",
        "content", "题干",
        "options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")),
        "answer", "AB")));
    assertThat(multiAnswer.getStatus()).isEqualTo(400);
    assertThat(multiAnswer.getMessage()).contains("单选题");

    // 题干为空
    ApiException emptyContent = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId,
        "type", "SINGLE",
        "content", "   ",
        "options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")),
        "answer", "A")));
    assertThat(emptyContent.getStatus()).isEqualTo(400);
    assertThat(emptyContent.getMessage()).contains("题干");
  }

  @Test
  void normalUserCannotAddQuestionsToPublicBank() {
    String userId = createUser("USER");
    long publicBankId = createBank(null, "公共题库");
    long unitId = createUnit(publicBankId, "公共单元");
    authenticate(userId, false);

    ApiException error = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId,
        "type", "SINGLE",
        "content", "想往公共题库塞一道题",
        "options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")),
        "answer", "A")));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(questionCount(unitId)).isZero();
  }

  @Test
  void cannotEditSomeoneElsesQuestion() {
    String ownerId = createUser("USER");
    long unitId = createUnit(createBank(ownerId, "别人的题库"), "第一章");
    long questionId = insertQuestion(unitId, "SINGLE", "原题干");

    authenticate(createUser("USER"), false);
    ApiException error = assertThrows(ApiException.class, () -> controller.updateQuestion(
        questionId,
        Map.of("type", "SINGLE", "content", "被改掉的题干", "answer", "A",
            "options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")))));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(jdbc.queryForObject("select content from question where id = ?", String.class, questionId))
        .isEqualTo("原题干");
  }

  @Test
  void unknownUnitIsNotFound() {
    authenticate(createUser("USER"), false);

    ApiException error = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", 999_999_999L,
        "type", "SINGLE",
        "content", "题干",
        "options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")),
        "answer", "A")));

    assertThat(error.getStatus()).isEqualTo(404);
  }

  private long createQuestion(Map<String, Object> body) {
    return ((Number) controller.createQuestion(body).data().get("id")).longValue();
  }

  private void authenticate(String userId, boolean admin) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()));
  }

  private String createUser(String role) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', ?, 'ENABLED')
        """, id, "bankquestion-" + id.substring(0, 8) + "@example.com", role);
    return id;
  }

  private long createBank(String ownerId, String name) {
    String unique = name + "-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", unique, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, unique);
  }

  private long createUnit(long bankId, String name) {
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 0)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private long insertQuestion(long unitId, String type, String content) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, ?, ?, cast('[{"key":"A","text":"a"},{"key":"B","text":"b"}]' as json),
                'A', cast('[]' as json), 'MEDIUM', 'ON')
        """, unitId, type, content);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? and content = ?", Long.class, unitId, content);
  }

  private int questionCount(long unitId) {
    return jdbc.queryForObject(
        "select count(*) from question where unit_id = ?", Integer.class, unitId);
  }
}
