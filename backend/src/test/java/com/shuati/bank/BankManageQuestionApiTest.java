package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.LinkedHashMap;
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
  void ownerCanCreateAndEditTrueFalseQuestionWithCanonicalAnswer() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "判断题题库"), "第一章");
    authenticate(userId, false);

    long questionId = createQuestion(Map.of(
        "unitId", unitId,
        "type", "TRUE_FALSE",
        "content", "Spring Bean 默认是单例。",
        "options", List.of(
            Map.of("key", "A", "text", "错误选项"),
            Map.of("key", "B", "text", "另一个错误选项")),
        "answer", "正确"));

    Map<String, Object> created = jdbc.queryForMap(
        "select type, cast(options as char) options, answer from question where id = ?", questionId);
    assertThat(created.get("type")).isEqualTo("TRUE_FALSE");
    assertThat(String.valueOf(created.get("options")))
        .contains("\"key\": \"T\"")
        .contains("\"text\": \"正确\"")
        .contains("\"key\": \"F\"")
        .contains("\"text\": \"错误\"");
    assertThat(created.get("answer")).isEqualTo("T");

    controller.updateQuestion(questionId, Map.of(
        "type", "TRUE_FALSE",
        "content", "Spring Bean 默认是原型。",
        "answer", "×"));

    Map<String, Object> updated = jdbc.queryForMap(
        "select content, answer from question where id = ?", questionId);
    assertThat(updated.get("content")).isEqualTo("Spring Bean 默认是原型。");
    assertThat(updated.get("answer")).isEqualTo("F");
  }

  @Test
  void trueFalseQuestionRejectsUnknownOrDoubleAnswer() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "判断题题库"), "第一章");
    authenticate(userId, false);

    ApiException unknown = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId,
        "type", "TRUE_FALSE",
        "content", "判断题",
        "answer", "也许")));
    assertThat(unknown.getStatus()).isEqualTo(400);
    assertThat(unknown.getMessage()).contains("判断题");

    ApiException doubleAnswer = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId,
        "type", "TRUE_FALSE",
        "content", "判断题",
        "answer", "正确错误")));
    assertThat(doubleAnswer.getStatus()).isEqualTo(400);
  }

  @Test
  void questionWithoutUnitFallsIntoTheDefaultUnit() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    authenticate(userId, false);

    long first = createQuestion(choiceQuestion(Map.of("bankId", bankId, "content", "第一题")));
    long second = createQuestion(choiceQuestion(Map.of("bankId", bankId, "content", "第二题")));

    long unitId = unitOf(first);
    assertThat(unitName(unitId)).isEqualTo("未分类");
    // 第二次还要落回同一个兜底单元，不能每道题建一个
    assertThat(unitOf(second)).isEqualTo(unitId);
    assertThat(jdbc.queryForObject(
        "select count(*) from unit where bank_id = ? and name = '未分类'", Integer.class, bankId))
        .isEqualTo(1);
  }

  @Test
  void questionWithoutUnitNeedsTheBank() {
    authenticate(createUser("USER"), false);

    ApiException error = assertThrows(ApiException.class,
        () -> createQuestion(choiceQuestion(Map.of("content", "没给题库"))));

    assertThat(error.getStatus()).isEqualTo(400);
    assertThat(error.getMessage()).contains("题库");
  }

  @Test
  void normalUserCannotAddQuestionToPublicBankByBankId() {
    String userId = createUser("USER");
    long publicBankId = createBank(null, "公共题库");
    authenticate(userId, false);

    ApiException error = assertThrows(ApiException.class,
        () -> createQuestion(choiceQuestion(Map.of("bankId", publicBankId, "content", "想塞进公共题库"))));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(questionCountOfBank(publicBankId)).isZero();
  }

  @Test
  void shortQuestionNeedsReferenceAnswerButKeyPointsAreOptional() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "我的题库"), "第一章");
    authenticate(userId, false);

    // 参考答案仍然必填
    ApiException noAnswer = assertThrows(ApiException.class, () -> createQuestion(Map.of(
        "unitId", unitId, "type", "SHORT", "content", "说说索引的作用", "answer", "  ")));
    assertThat(noAnswer.getStatus()).isEqualTo(400);
    assertThat(noAnswer.getMessage()).contains("答案");

    // 不填评分要点也能建：判分时由 AI 自行提炼
    long withoutKeyPoints = createQuestion(Map.of(
        "unitId", unitId, "type", "SHORT", "content", "说说索引的作用", "answer", "参考答案"));
    assertThat(jdbc.queryForObject(
        "select key_points from question where id = ?", Object.class, withoutKeyPoints)).isNull();

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

  /** 回放线上那条路径：编辑一道没有评分要点的简答题，keyPoints 显式为 null */
  @Test
  void ownerCanEditShortQuestionWithNullKeyPoints() {
    String userId = createUser("USER");
    long unitId = createUnit(createBank(userId, "我的题库"), "简答");
    authenticate(userId, false);

    long questionId = createQuestion(Map.of(
        "unitId", unitId, "type", "SHORT", "content", "旧题干", "answer", "旧参考答案"));

    // 前端编辑时提交的结构：keyPoints / options 都是 null（Map.of 不收 null，所以用 LinkedHashMap）
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("bankId", 0L);
    body.put("unitId", unitId);
    body.put("type", "SHORT");
    body.put("content", "新题干");
    body.put("answer", "新参考答案");
    body.put("options", null);
    body.put("keyPoints", null);
    body.put("difficulty", "HARD");
    body.put("explanation", "");
    body.put("tags", List.of());

    controller.updateQuestion(questionId, body);

    Map<String, Object> row = jdbc.queryForMap(
        "select content, answer, difficulty, key_points from question where id = ?", questionId);
    assertThat(row.get("content")).isEqualTo("新题干");
    assertThat(row.get("answer")).isEqualTo("新参考答案");
    assertThat(row.get("difficulty")).isEqualTo("HARD");
    assertThat(row.get("key_points")).isNull();
  }

  /** 编辑弹窗里的「所属单元」下拉框要真的能换单元 */
  @Test
  void ownerCanMoveQuestionToAnotherUnitOfTheSameBank() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long firstUnit = createUnit(bankId, "第一章");
    long secondUnit = createUnit(bankId, "第二章");
    authenticate(userId, false);
    long questionId = insertQuestion(firstUnit, "SINGLE", "换单元的题");

    controller.updateQuestion(
        questionId, choiceQuestion(Map.of("unitId", secondUnit, "content", "换单元的题")));

    assertThat(unitOf(questionId)).isEqualTo(secondUnit);
    assertThat(questionCountOfBank(bankId)).isEqualTo(1);
  }

  /** 不传 unitId 的老请求不能把题目挪走 */
  @Test
  void editingWithoutUnitIdKeepsTheCurrentUnit() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);
    long questionId = insertQuestion(unitId, "SINGLE", "原题干");

    controller.updateQuestion(questionId, choiceQuestion(Map.of("content", "只改题干")));

    assertThat(unitOf(questionId)).isEqualTo(unitId);
  }

  /** 下拉框选「默认单元（未分类）」传 null，落到兜底单元 */
  @Test
  void explicitNullUnitIdMovesQuestionToDefaultUnit() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);
    long questionId = insertQuestion(unitId, "SINGLE", "挪进未分类");

    Map<String, Object> body = new LinkedHashMap<>(choiceQuestion(Map.of("content", "挪进未分类")));
    body.put("unitId", null);
    controller.updateQuestion(questionId, body);

    long movedTo = unitOf(questionId);
    assertThat(movedTo).isNotEqualTo(unitId);
    assertThat(unitName(movedTo)).isEqualTo("未分类");
  }

  /** 不能把题目挪到别人的题库里 */
  @Test
  void cannotMoveQuestionIntoAnotherBank() {
    String userId = createUser("USER");
    long myUnit = createUnit(createBank(userId, "我的题库"), "第一章");
    long foreignUnit = createUnit(createBank(userId, "我的另一个题库"), "别的单元");
    authenticate(userId, false);
    long questionId = insertQuestion(myUnit, "SINGLE", "不许跨库");

    ApiException error = assertThrows(ApiException.class, () -> controller.updateQuestion(
        questionId, choiceQuestion(Map.of("unitId", foreignUnit, "content", "不许跨库"))));

    assertThat(error.getStatus()).isEqualTo(400);
    assertThat(unitOf(questionId)).isEqualTo(myUnit);
  }

  /** 题库内检索：只返回题干命中的题目，空关键词等于不筛选 */
  @Test
  void searchReturnsOnlyMatchingQuestions() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);
    long indexQuestion = insertQuestion(unitId, "SINGLE", "索引的作用是什么？");
    insertQuestion(unitId, "SINGLE", "事务的隔离级别有几个？");

    List<Map<String, Object>> hit = controller.questions(bankId, "ALL", "索引").data();
    assertThat(hit).hasSize(1);
    assertThat(hit.get(0).get("id")).isEqualTo(indexQuestion);

    // 空串 / 纯空格都等于不筛选
    assertThat(controller.questions(bankId, "ALL", "").data()).hasSize(2);
    assertThat(controller.questions(bankId, "ALL", "   ").data()).hasSize(2);
  }

  /** 检索词里的 % 是普通字符，不能把整个题库捞回来 */
  @Test
  void searchDoesNotTreatPercentAsWildcard() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);
    insertQuestion(unitId, "SINGLE", "正常题目一");
    insertQuestion(unitId, "SINGLE", "正常题目二");
    insertQuestion(unitId, "SINGLE", "命中率 90% 的那道题");

    List<Map<String, Object>> hit = controller.questions(bankId, "ALL", "%").data();
    assertThat(hit).hasSize(1);
    assertThat(String.valueOf(hit.get(0).get("content"))).contains("90%");
  }

  /** 检索叠加状态过滤，且不放宽权限 */
  @Test
  void searchStacksWithStatusFilterAndStillChecksPermission() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    long unitId = createUnit(bankId, "第一章");
    authenticate(userId, false);
    long onQuestion = insertQuestion(unitId, "SINGLE", "索引的作用是什么？");
    long offQuestion = insertQuestion(unitId, "SINGLE", "索引失效的常见原因？");
    jdbc.update("update question set status = 'OFF' where id = ?", offQuestion);

    assertThat(idsOf(controller.questions(bankId, "ON", "索引").data())).containsExactly(onQuestion);
    assertThat(idsOf(controller.questions(bankId, "OFF", "索引").data())).containsExactly(offQuestion);

    long otherBankId = createBank(createUser("USER"), "别人的题库");
    ApiException error = assertThrows(
        ApiException.class, () -> controller.questions(otherBankId, "ALL", "索引"));
    assertThat(error.getStatus()).isEqualTo(403);
    // 列表按 q.id 倒序，后插入的排在前面
    assertThat(idsOf(controller.questions(bankId, "ALL", "索引").data()))
        .containsExactly(offQuestion, onQuestion);
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

  /** 一道能过校验的单选，用例只覆盖自己关心的字段 */
  private Map<String, Object> choiceQuestion(Map<String, Object> extra) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "SINGLE");
    body.put("answer", "A");
    body.put("options", List.of(Map.of("key", "A", "text", "a"), Map.of("key", "B", "text", "b")));
    body.putAll(extra);
    return body;
  }

  private long unitOf(long questionId) {
    return jdbc.queryForObject("select unit_id from question where id = ?", Long.class, questionId);
  }

  /** 列表接口返回的行里取出 id，方便断言筛选结果 */
  private List<Object> idsOf(List<Map<String, Object>> rows) {
    return rows.stream().map(row -> row.get("id")).toList();
  }

  private String unitName(long unitId) {
    return jdbc.queryForObject("select name from unit where id = ?", String.class, unitId);
  }

  private int questionCountOfBank(long bankId) {
    return jdbc.queryForObject("""
        select count(*) from question q join unit u on u.id = q.unit_id where u.bank_id = ?
        """, Integer.class, bankId);
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
