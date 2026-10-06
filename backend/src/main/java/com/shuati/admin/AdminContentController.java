package com.shuati.admin;

import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
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

@RestController
@RequiredArgsConstructor
public class AdminContentController {

  private static final Set<String> QUESTION_JSON = Set.of("options", "key_points", "images", "tags");

  private final JdbcTemplate jdbc;
  private final Json json;

  @GetMapping("/api/admin/banks")
  public ApiResponse<List<Map<String, Object>>> banks() {
    List<Map<String, Object>> banks = json.normalize(jdbc.queryForList(
        "select * from bank order by is_default desc, id asc"), Set.of());
    List<Map<String, Object>> units = jdbc.queryForList("select id, bank_id from unit");
    List<Map<String, Object>> questions = jdbc.queryForList("select id, unit_id from question");
    Map<Long, Integer> unitCount = new LinkedHashMap<>();
    Map<Long, Integer> questionCount = new LinkedHashMap<>();
    for (Map<String, Object> unit : units) {
      unitCount.merge(((Number) unit.get("bank_id")).longValue(), 1, Integer::sum);
    }
    for (Map<String, Object> question : questions) {
      questionCount.merge(((Number) question.get("unit_id")).longValue(), 1, Integer::sum);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> bank : banks) {
      long bankId = ((Number) bank.get("id")).longValue();
      int questionTotal = units.stream()
          .filter(u -> ((Number) u.get("bank_id")).longValue() == bankId)
          .mapToInt(u -> questionCount.getOrDefault(((Number) u.get("id")).longValue(), 0))
          .sum();
      Map<String, Object> view = new LinkedHashMap<>(bank);
      view.put("is_public", bank.get("owner_id") == null);
      view.put("unit_count", unitCount.getOrDefault(bankId, 0));
      view.put("question_count", questionTotal);
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  @PostMapping("/api/admin/banks")
  public ApiResponse<Void> createBank(@RequestBody Map<String, Object> body) {
    String name = string(body.get("name")).trim();
    if (name.isEmpty()) {
      throw new ApiException(400, "题库名不能为空");
    }
    if (jdbc.queryForObject(
        "select count(*) from bank where owner_id is null and name = ?", Integer.class, name) > 0) {
      throw new ApiException(400, "题库名已存在");
    }
    jdbc.update("insert into bank (name, description, is_default, owner_id) values (?, ?, 0, null)",
        name, body.get("description") == null ? null : string(body.get("description")));
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/admin/banks/{id}")
  public ApiResponse<Void> updateBank(@PathVariable long id, @RequestBody Map<String, Object> body) {
    String name = string(body.get("name")).trim();
    String description = body.get("description") == null ? null : string(body.get("description"));
    int updated = jdbc.update(
        "update bank set name = coalesce(nullif(?, ''), name), description = ? where id = ?",
        name, description, id);
    if (updated == 0) {
      throw new ApiException(404, "题库不存在");
    }
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/admin/banks/{id}")
  public ApiResponse<Void> deleteBank(@PathVariable long id) {
    jdbc.update("delete from bank where id = ?", id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/api/admin/banks/{id}/default")
  public ApiResponse<Void> setDefaultBank(@PathVariable long id) {
    Integer exists = jdbc.queryForObject("select count(*) from bank where id = ?", Integer.class, id);
    if (exists == null || exists == 0) {
      throw new ApiException(404, "题库不存在");
    }
    jdbc.update("update bank set is_default = 0 where is_default = 1");
    jdbc.update("update bank set is_default = 1 where id = ?", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/admin/units")
  public ApiResponse<List<Map<String, Object>>> units(@RequestParam long bankId) {
    List<Map<String, Object>> units = jdbc.queryForList(
        "select id, bank_id, name, sort from unit where bank_id = ? order by sort asc, id asc", bankId);
    List<Map<String, Object>> questions = jdbc.queryForList(
        "select id, unit_id from question");
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> unit : units) {
      long unitId = ((Number) unit.get("id")).longValue();
      Map<String, Object> view = new LinkedHashMap<>(unit);
      view.put("question_count", questions.stream()
          .filter(q -> ((Number) q.get("unit_id")).longValue() == unitId).count());
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  @PostMapping("/api/admin/units")
  public ApiResponse<Void> createUnit(@RequestBody Map<String, Object> body) {
    long bankId = longValue(body.get("bankId"));
    String name = string(body.get("name")).trim();
    if (bankId == 0) {
      throw new ApiException(400, "请先选择题库");
    }
    if (name.isEmpty()) {
      throw new ApiException(400, "单元名不能为空");
    }
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, ?)",
        bankId, name, intValue(body.get("sort")));
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/admin/units/{id}")
  public ApiResponse<Void> updateUnit(@PathVariable long id, @RequestBody Map<String, Object> body) {
    String name = string(body.get("name")).trim();
    jdbc.update("update unit set name = coalesce(nullif(?, ''), name), sort = ? where id = ?",
        name, intValue(body.get("sort")), id);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/admin/units/{id}")
  public ApiResponse<Void> deleteUnit(@PathVariable long id) {
    jdbc.update("delete from unit where id = ?", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/admin/questions")
  public ApiResponse<List<Map<String, Object>>> questions(
      @RequestParam(required = false) Long bankId,
      @RequestParam(required = false) Long unitId) {
    List<Map<String, Object>> rows;
    if (unitId != null) {
      rows = jdbc.queryForList(
          "select * from question where unit_id = ? order by id desc limit 2000", unitId);
    } else if (bankId != null) {
      rows = jdbc.queryForList("""
          select q.* from question q join unit u on u.id = q.unit_id
           where u.bank_id = ? order by q.id desc limit 2000
          """, bankId);
    } else {
      rows = jdbc.queryForList("select * from question order by id desc limit 2000");
    }
    return ApiResponse.ok(json.normalize(rows, QUESTION_JSON));
  }

  @PostMapping("/api/admin/questions")
  public ApiResponse<Void> createQuestion(@RequestBody Map<String, Object> body) {
    long unitId = longValue(body.get("unitId"));
    String type = string(body.get("type")).toUpperCase();
    String content = string(body.get("content")).trim();
    String answer = string(body.get("answer")).trim();
    if (unitId == 0) {
      throw new ApiException(400, "请先选择单元");
    }
    if (content.isEmpty()) {
      throw new ApiException(400, "题干不能为空");
    }
    validateQuestion(type, body.get("options"), answer, body.get("keyPoints"));
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, explanation, tags, status)
        values (?, ?, ?, cast(? as json), ?, cast(? as json), ?, ?, cast(? as json), 'ON')
        """, unitId, type, content, writeJson(body.get("options")), answer,
        writeJson(body.get("keyPoints")), normalizeDifficulty(body.get("difficulty")),
        body.get("explanation") == null ? null : string(body.get("explanation")),
        writeJson(body.get("tags") == null ? List.of() : body.get("tags")));
    return ApiResponse.ok(null);
  }

  @PutMapping("/api/admin/questions/{id}")
  public ApiResponse<Void> updateQuestion(@PathVariable long id, @RequestBody Map<String, Object> body) {
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

  @DeleteMapping("/api/admin/questions/{id}")
  public ApiResponse<Void> deleteQuestion(@PathVariable long id) {
    jdbc.update("delete from question where id = ?", id);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/admin/questions/{id}/status")
  public ApiResponse<Void> questionStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
    String status = string(body.get("status")).toUpperCase();
    if (!List.of("ON", "OFF").contains(status)) {
      throw new ApiException(400, "状态只能是 ON / OFF");
    }
    jdbc.update("update question set status = ? where id = ?", status, id);
    return ApiResponse.ok(null);
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

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
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

  private int intValue(Object value) {
    return (int) longValue(value);
  }
}
