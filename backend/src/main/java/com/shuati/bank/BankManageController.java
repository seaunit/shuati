package com.shuati.bank;

import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库维护（面向所有登录用户）。
 *
 * <p>权限规则：自己的题库可以维护；公共题库（owner_id 为空）只有管理员可以维护；
 * 别人的私有题库既看不到也改不了。单元与题目跟随所属题库的权限。
 *
 * <p>管理员后台那套 /api/admin/** 保持不动，它管的是全站题库；这里只暴露
 * 「当前用户有权限维护」的那部分，所有写操作都先过 requireManage。
 */
@RestController
@RequiredArgsConstructor
public class BankManageController {

  private static final Set<String> QUESTION_JSON = Set.of("options", "key_points", "images", "tags");

  /** 不选单元时的兜底单元名，与解析导入里的兜底保持一致 */
  private static final String DEFAULT_UNIT_NAME = "未分类";

  private final JdbcTemplate jdbc;
  private final Json json;
  private final PointAccountService points;

  /** 我能维护的题库：自己的，加上管理员能看到的公共题库 */
  @GetMapping("/api/manage/banks")
  public ApiResponse<List<Map<String, Object>>> banks() {
    String userId = CurrentUser.id();
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, name, description, owner_id, is_default, created_at
          from bank
         where owner_id = ? or (? = 1 and owner_id is null)
         order by owner_id is null desc, id asc
        """, userId, CurrentUser.isAdmin() ? 1 : 0);
    List<Map<String, Object>> units = jdbc.queryForList("select id, bank_id from unit");
    List<Map<String, Object>> questions = jdbc.queryForList("select id, unit_id from question");

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      long bankId = ((Number) row.get("id")).longValue();
      List<Long> unitIds = units.stream()
          .filter(unit -> ((Number) unit.get("bank_id")).longValue() == bankId)
          .map(unit -> ((Number) unit.get("id")).longValue())
          .toList();
      Map<String, Object> view = new LinkedHashMap<>(json.normalize(row, Set.of()));
      view.put("is_public", row.get("owner_id") == null);
      view.put("unit_count", unitIds.size());
      view.put("question_count", questions.stream()
          .filter(q -> unitIds.contains(((Number) q.get("unit_id")).longValue()))
          .count());
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  @PostMapping("/api/manage/banks")
  public ApiResponse<Map<String, Object>> createBank(@RequestBody Map<String, Object> body) {
    String userId = CurrentUser.id();
    String name = string(body.get("name")).trim();
    if (name.isEmpty()) {
      throw new ApiException(400, "题库名称不能为空");
    }
    String description = body.get("description") == null ? null : string(body.get("description")).trim();
    // 新建题库占用套餐的题库额度（往已有题库加题不占）
    points.assertBankQuota(userId, points.entitlements(userId));
    if (nameTaken(userId, false, name, 0L)) {
      throw new ApiException(400, "你已经有一个同名题库了");
    }

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement(
          "insert into bank (name, description, is_default, owner_id) values (?, ?, 0, ?)",
          new String[]{"id"});
      ps.setString(1, name);
      ps.setString(2, description);
      ps.setString(3, userId);
      return ps;
    }, keyHolder);
    long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    return ApiResponse.ok(Map.of("id", id));
  }

  @PatchMapping("/api/manage/banks/{id}")
  public ApiResponse<Void> updateBank(@PathVariable long id, @RequestBody Map<String, Object> body) {
    Map<String, Object> bank = requireManage(id);
    String name = string(body.get("name")).trim();
    boolean publicBank = bank.get("owner_id") == null;
    if (!name.isEmpty()
        && nameTaken(string(bank.get("owner_id")), publicBank, name, id)) {
      throw new ApiException(400, "已有同名题库，请换一个名字");
    }
    String description = body.get("description") == null ? null : string(body.get("description"));
    jdbc.update(
        "update bank set name = coalesce(nullif(?, ''), name), description = ? where id = ?",
        name, description, id);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/manage/banks/{id}")
  public ApiResponse<Void> deleteBank(@PathVariable long id) {
    requireManage(id);
    Integer questions = jdbc.queryForObject("""
        select count(*) from question q join unit u on u.id = q.unit_id where u.bank_id = ?
        """, Integer.class, id);
    if (questions != null && questions > 0) {
      // unit.bank_id 是级联删除，但 question.unit_id 是 RESTRICT，直接删会变成 500
      throw new ApiException(400, "该题库下还有 " + questions + " 道题，请先清空题目再删除题库");
    }
    jdbc.update("delete from unit where bank_id = ?", id);
    jdbc.update("delete from bank where id = ?", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/manage/banks/{id}/units")
  public ApiResponse<List<Map<String, Object>>> units(@PathVariable long id) {
    requireManage(id);
    List<Map<String, Object>> units = jdbc.queryForList(
        "select id, bank_id, name, sort from unit where bank_id = ? order by sort asc, id asc", id);
    List<Map<String, Object>> questions = jdbc.queryForList("""
        select q.unit_id, q.status from question q join unit u on u.id = q.unit_id
         where u.bank_id = ?
        """, id);

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> unit : units) {
      long unitId = ((Number) unit.get("id")).longValue();
      Map<String, Object> view = new LinkedHashMap<>(unit);
      view.put("question_count", questions.stream()
          .filter(q -> ((Number) q.get("unit_id")).longValue() == unitId).count());
      view.put("off_count", questions.stream()
          .filter(q -> ((Number) q.get("unit_id")).longValue() == unitId)
          .filter(q -> "OFF".equals(q.get("status"))).count());
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  @PostMapping("/api/manage/units")
  public ApiResponse<Map<String, Object>> createUnit(@RequestBody Map<String, Object> body) {
    long bankId = longValue(body.get("bankId"));
    String name = string(body.get("name")).trim();
    if (bankId == 0) {
      throw new ApiException(400, "请先选择题库");
    }
    if (name.isEmpty()) {
      throw new ApiException(400, "单元名不能为空");
    }
    requireManage(bankId);
    if (sameUnitName(bankId, name, 0L)) {
      throw new ApiException(400, "该题库下已有同名单元");
    }
    Integer nextSort = jdbc.queryForObject(
        "select coalesce(max(sort), -1) + 1 from unit where bank_id = ?", Integer.class, bankId);

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement(
          "insert into unit (bank_id, name, sort) values (?, ?, ?)", new String[]{"id"});
      ps.setLong(1, bankId);
      ps.setString(2, name);
      ps.setInt(3, nextSort == null ? 0 : nextSort);
      return ps;
    }, keyHolder);
    long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    return ApiResponse.ok(Map.of("id", id));
  }

  @PatchMapping("/api/manage/units/{id}")
  public ApiResponse<Void> updateUnit(@PathVariable long id, @RequestBody Map<String, Object> body) {
    long bankId = bankIdOfUnit(id);
    requireManage(bankId);
    String name = string(body.get("name")).trim();
    if (!name.isEmpty() && sameUnitName(bankId, name, id)) {
      throw new ApiException(400, "该题库下已有同名单元");
    }
    // sort 没传就不动，避免把已有排序重置成 0
    if (body.get("sort") == null) {
      jdbc.update("update unit set name = coalesce(nullif(?, ''), name) where id = ?", name, id);
    } else {
      jdbc.update("update unit set name = coalesce(nullif(?, ''), name), sort = ? where id = ?",
          name, intValue(body.get("sort")), id);
    }
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/manage/units/{id}")
  public ApiResponse<Void> deleteUnit(@PathVariable long id) {
    long bankId = bankIdOfUnit(id);
    requireManage(bankId);
    Integer questions = jdbc.queryForObject(
        "select count(*) from question where unit_id = ?", Integer.class, id);
    if (questions != null && questions > 0) {
      throw new ApiException(400, "该单元下还有 " + questions + " 道题，请先清空题目再删除单元");
    }
    jdbc.update("delete from unit where id = ?", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/manage/banks/{id}/questions")
  public ApiResponse<List<Map<String, Object>>> questions(
      @PathVariable long id, @RequestParam(defaultValue = "ALL") String status) {
    requireManage(id);
    String filter = status.toUpperCase();
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.* from question q join unit u on u.id = q.unit_id
         where u.bank_id = ? and (? not in ('ON', 'OFF') or q.status = ?)
         order by q.id desc limit 500
        """, id, filter, filter);
    return ApiResponse.ok(json.normalize(rows, QUESTION_JSON));
  }

  /** 手工新增题目（AI 解析之外的另一条录入路径） */
  @PostMapping("/api/manage/questions")
  public ApiResponse<Map<String, Object>> createQuestion(@RequestBody Map<String, Object> body) {
    long unitId = longValue(body.get("unitId"));
    // 单元不是必填：不选就落到该题库的「未分类」兜底单元，所以这时必须知道是哪个题库
    if (unitId != 0) {
      requireManage(bankIdOfUnit(unitId));
    } else {
      long bankId = longValue(body.get("bankId"));
      if (bankId == 0) {
        throw new ApiException(400, "请选择题库");
      }
      requireManage(bankId);
      unitId = defaultUnitId(bankId);
    }
    String type = string(body.get("type")).toUpperCase();
    String content = string(body.get("content")).trim();
    String answer = string(body.get("answer")).trim();
    if (content.isEmpty()) {
      throw new ApiException(400, "题干不能为空");
    }
    validateQuestion(type, body.get("options"), answer, body.get("keyPoints"));

    long targetUnitId = unitId;
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into question
            (unit_id, type, content, options, answer, key_points, difficulty, explanation, tags, status)
          values (?, ?, ?, cast(? as json), ?, cast(? as json), ?, ?, cast(? as json), 'ON')
          """, new String[]{"id"});
      ps.setLong(1, targetUnitId);
      ps.setString(2, type);
      ps.setString(3, content);
      ps.setString(4, writeJson(body.get("options")));
      ps.setString(5, answer);
      ps.setString(6, writeJson(body.get("keyPoints")));
      ps.setString(7, normalizeDifficulty(body.get("difficulty")));
      ps.setString(8, body.get("explanation") == null ? null : string(body.get("explanation")));
      ps.setString(9, writeJson(body.get("tags") == null ? List.of() : body.get("tags")));
      return ps;
    }, keyHolder);
    long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    return ApiResponse.ok(Map.of("id", id));
  }

  @PutMapping("/api/manage/questions/{id}")
  public ApiResponse<Void> updateQuestion(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    requireManage(bankIdOfQuestion(id));
    String type = string(body.get("type")).toUpperCase();
    String content = string(body.get("content")).trim();
    String answer = string(body.get("answer")).trim();
    if (content.isEmpty()) {
      throw new ApiException(400, "题干不能为空");
    }
    validateQuestion(type, body.get("options"), answer, body.get("keyPoints"));
    jdbc.update("""
        update question
           set type = ?, content = ?, options = cast(? as json), answer = ?,
               key_points = cast(? as json), difficulty = ?, explanation = ?,
               tags = cast(? as json)
         where id = ?
        """, type, content, writeJson(body.get("options")), answer,
        writeJson(body.get("keyPoints")), normalizeDifficulty(body.get("difficulty")),
        body.get("explanation") == null ? null : string(body.get("explanation")),
        writeJson(body.get("tags") == null ? List.of() : body.get("tags")), id);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/manage/questions/{id}/status")
  public ApiResponse<Void> questionStatus(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    requireManage(bankIdOfQuestion(id));
    String status = string(body.get("status")).toUpperCase();
    if (!List.of("ON", "OFF").contains(status)) {
      throw new ApiException(400, "状态只能是 ON / OFF");
    }
    jdbc.update("update question set status = ? where id = ?", status, id);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/manage/questions/{id}")
  public ApiResponse<Void> deleteQuestion(@PathVariable long id) {
    requireManage(bankIdOfQuestion(id));
    jdbc.update("delete from question where id = ?", id);
    return ApiResponse.ok(null);
  }

  /** 校验维护权限并返回题库行；无权抛 403，不存在抛 404。 */
  private Map<String, Object> requireManage(long bankId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select id, name, owner_id from bank where id = ?", bankId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "题库不存在");
    }
    Map<String, Object> bank = rows.get(0);
    Object ownerId = bank.get("owner_id");
    if (ownerId == null) {
      if (!CurrentUser.isAdmin()) {
        throw new ApiException(403, "公共题库只能由管理员维护");
      }
      return bank;
    }
    if (!String.valueOf(ownerId).equals(CurrentUser.id())) {
      throw new ApiException(403, "无权维护该题库");
    }
    return bank;
  }

  /** 重名检查；公共题库的 owner_id 为空，MySQL 唯一索引不约束 NULL，所以要自己查。 */
  private boolean nameTaken(String ownerId, boolean publicBank, String name, long excludeId) {
    Integer count = publicBank
        ? jdbc.queryForObject("""
            select count(*) from bank where id <> ? and owner_id is null and name = ?
            """, Integer.class, excludeId, name)
        : jdbc.queryForObject("""
            select count(*) from bank where id <> ? and owner_id = ? and name = ?
            """, Integer.class, excludeId, ownerId, name);
    return count != null && count > 0;
  }

  private boolean sameUnitName(long bankId, String name, long excludeId) {
    Integer count = jdbc.queryForObject("""
        select count(*) from unit where id <> ? and bank_id = ? and name = ?
        """, Integer.class, excludeId, bankId, name);
    return count != null && count > 0;
  }

  private long bankIdOfUnit(long unitId) {
    List<Long> ids = jdbc.queryForList("select bank_id from unit where id = ?", Long.class, unitId);
    if (ids.isEmpty()) {
      throw new ApiException(404, "单元不存在");
    }
    return ids.get(0);
  }

  /** 「未分类」兜底单元：同一个题库只建一个，复用它。 */
  private long defaultUnitId(long bankId) {
    List<Long> ids = jdbc.queryForList(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, DEFAULT_UNIT_NAME);
    if (!ids.isEmpty()) {
      return ids.get(0);
    }
    Integer nextSort = jdbc.queryForObject(
        "select coalesce(max(sort), -1) + 1 from unit where bank_id = ?", Integer.class, bankId);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement(
          "insert into unit (bank_id, name, sort) values (?, ?, ?)", new String[]{"id"});
      ps.setLong(1, bankId);
      ps.setString(2, DEFAULT_UNIT_NAME);
      ps.setInt(3, nextSort == null ? 0 : nextSort);
      return ps;
    }, keyHolder);
    return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
  }

  private long bankIdOfQuestion(long questionId) {
    List<Long> ids = jdbc.queryForList("""
        select u.bank_id from question q join unit u on u.id = q.unit_id where q.id = ?
        """, Long.class, questionId);
    if (ids.isEmpty()) {
      throw new ApiException(404, "题目不存在");
    }
    return ids.get(0);
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private void validateQuestion(String type, Object options, String answer, Object keyPoints) {
    if (!List.of("SINGLE", "MULTI", "SHORT").contains(type)) {
      throw new ApiException(400, "题型不正确");
    }
    if (answer.isEmpty()) {
      throw new ApiException(400, "答案不能为空");
    }
    if (("SINGLE".equals(type) || "MULTI".equals(type))
        && !(options instanceof List<?> list && !list.isEmpty())) {
      throw new ApiException(400, "选择题必须提供选项");
    }
    if ("SINGLE".equals(type) && answer.length() != 1) {
      throw new ApiException(400, "单选题答案只能是一个选项");
    }
    if ("SHORT".equals(type) && !(keyPoints instanceof List<?> list && !list.isEmpty())) {
      throw new ApiException(400, "简答题必须提供评分要点");
    }
  }

  private String normalizeDifficulty(Object value) {
    String difficulty = string(value).toUpperCase();
    return List.of("EASY", "MEDIUM", "HARD").contains(difficulty) ? difficulty : "MEDIUM";
  }

  private String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return json.objectMapper().writeValueAsString(value);
    } catch (Exception e) {
      return null;
    }
  }

  private int intValue(Object value) {
    return (int) longValue(value);
  }

  private long longValue(Object value) {
    if (value == null) {
      return 0;
    }
    try {
      return (long) Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
