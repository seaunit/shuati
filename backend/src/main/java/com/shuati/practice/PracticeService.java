package com.shuati.practice;

import com.shuati.common.ApiException;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeService {

  private final JdbcTemplate jdbc;
  private final Json json;

  @Transactional
  public Map<String, Object> submitChoice(long questionId, Object rawSelected, Integer durationMs) {
    String selected = normalizeSelected(rawSelected);
    if (selected.isEmpty()) {
      throw new ApiException(400, "请先选择选项");
    }
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select type, answer, status from question where id = ?", questionId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "题目不存在或已下架");
    }
    Map<String, Object> question = rows.get(0);
    String type = String.valueOf(question.get("type"));
    if (!"ON".equals(question.get("status")) || (!"SINGLE".equals(type) && !"MULTI".equals(type))) {
      throw new ApiException(404, "题目不存在或已下架");
    }

    String answer = normalizeSelected(question.get("answer"));
    String verdict = answer.equals(selected) ? "CORRECT" : "WRONG";
    int score = "CORRECT".equals(verdict) ? 10 : 0;

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into practice_record
            (user_id, question_id, user_answer, judge_source, verdict, score, duration_ms)
          values (?, ?, ?, 'LOCAL', ?, ?, ?)
          """, new String[]{"id"});
      ps.setString(1, CurrentUser.id());
      ps.setLong(2, questionId);
      ps.setString(3, selected);
      ps.setString(4, verdict);
      ps.setInt(5, score);
      if (durationMs == null) {
        ps.setNull(6, java.sql.Types.INTEGER);
      } else {
        ps.setInt(6, durationMs);
      }
      return ps;
    }, keyHolder);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("recordId", keyHolder.getKey() == null ? null : keyHolder.getKey().longValue());
    result.put("verdict", verdict);
    result.put("score", score);
    result.put("correctAnswer", question.get("answer"));
    return result;
  }

  @Transactional
  public Map<String, Object> createSession(Map<String, Object> body) {
    String scopeType = String.valueOf(body.getOrDefault("scopeType", "ALL")).toUpperCase(Locale.ROOT);
    if (!List.of("ALL", "BANK", "UNIT").contains(scopeType)) {
      scopeType = "ALL";
    }
    Long scopeId = body.get("scopeId") == null ? null : Long.valueOf(String.valueOf(body.get("scopeId")));
    String scopeName = String.valueOf(body.getOrDefault("scopeName", ""));
    int totalQuestions = Math.max(0, intValue(body.get("totalQuestions")));

    KeyHolder keyHolder = new GeneratedKeyHolder();
    String finalScopeType = scopeType;
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into practice_session
            (user_id, scope_type, scope_id, scope_name, total_questions)
          values (?, ?, ?, ?, ?)
          """, new String[]{"id"});
      ps.setString(1, CurrentUser.id());
      ps.setString(2, finalScopeType);
      if (scopeId == null) {
        ps.setNull(3, java.sql.Types.BIGINT);
      } else {
        ps.setLong(3, scopeId);
      }
      ps.setString(4, scopeName);
      ps.setInt(5, totalQuestions);
      return ps;
    }, keyHolder);
    return Map.of("id", keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue());
  }

  public List<Map<String, Object>> listSessions() {
    return json.normalize(jdbc.queryForList("""
        select * from practice_session
         where user_id = ?
         order by started_at desc
         limit 50
        """, CurrentUser.id()), Set.of());
  }

  @Transactional
  public void patchSession(long id, Map<String, Object> body) {
    String status = String.valueOf(body.getOrDefault("status", "IN_PROGRESS")).toUpperCase(Locale.ROOT);
    if (!List.of("IN_PROGRESS", "COMPLETED", "ABANDONED").contains(status)) {
      status = "IN_PROGRESS";
    }
    int answered = Math.max(0, intValue(body.get("answered")));
    int correct = Math.max(0, intValue(body.get("correct")));
    int partial = Math.max(0, intValue(body.get("partial")));
    int wrong = Math.max(0, intValue(body.get("wrong")));
    Integer score = body.get("score") == null ? null
        : Math.max(0, Math.min(100, intValue(body.get("score"))));
    boolean finished = !"IN_PROGRESS".equals(status);

    int updated = jdbc.update("""
        update practice_session
           set answered = ?, correct = ?, partial = ?, wrong = ?, score = ?, status = ?,
               finished_at = case when ? then now(6) else finished_at end
         where id = ? and user_id = ?
        """, answered, correct, partial, wrong, score, status, finished, id, CurrentUser.id());
    if (updated == 0) {
      throw new ApiException(404, "练习记录不存在");
    }
  }

  public List<Map<String, Object>> wrongBook() {
    List<Map<String, Object>> records = jdbc.queryForList("""
        select id, question_id, verdict, score, created_at
          from practice_record
         where user_id = ?
         order by created_at desc
        """, CurrentUser.id());

    Map<Long, Map<String, Object>> latest = new LinkedHashMap<>();
    for (Map<String, Object> record : records) {
      long questionId = ((Number) record.get("question_id")).longValue();
      latest.putIfAbsent(questionId, record);
    }
    List<Map<String, Object>> wrong = latest.values().stream()
        .filter(r -> !"CORRECT".equals(r.get("verdict")))
        .toList();
    if (wrong.isEmpty()) {
      return List.of();
    }

    List<Long> questionIds = wrong.stream()
        .map(r -> ((Number) r.get("question_id")).longValue())
        .toList();
    String qPlaceholders = placeholders(questionIds.size());
    List<Map<String, Object>> questions = jdbc.queryForList("""
        select id, unit_id, type, content, difficulty
          from question where id in (%s)
        """.formatted(qPlaceholders), questionIds.toArray());
    Map<Long, Map<String, Object>> questionMap = index(questions);

    List<Long> unitIds = questions.stream()
        .map(q -> ((Number) q.get("unit_id")).longValue()).distinct().toList();
    List<Map<String, Object>> units = unitIds.isEmpty() ? List.of()
        : jdbc.queryForList("""
            select id, bank_id, name from unit where id in (%s)
            """.formatted(placeholders(unitIds.size())), unitIds.toArray());
    Map<Long, Map<String, Object>> unitMap = index(units);

    List<Long> bankIds = units.stream()
        .map(u -> ((Number) u.get("bank_id")).longValue()).distinct().toList();
    List<Map<String, Object>> banks = bankIds.isEmpty() ? List.of()
        : jdbc.queryForList("""
            select id, name from bank where id in (%s)
            """.formatted(placeholders(bankIds.size())), bankIds.toArray());
    Map<Long, Map<String, Object>> bankMap = index(banks);

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> record : wrong) {
      Map<String, Object> question = questionMap.get(
          ((Number) record.get("question_id")).longValue());
      Map<String, Object> unit = question == null ? null
          : unitMap.get(((Number) question.get("unit_id")).longValue());
      Map<String, Object> bank = unit == null ? null
          : bankMap.get(((Number) unit.get("bank_id")).longValue());

      Map<String, Object> view = new LinkedHashMap<>();
      view.put("record_id", record.get("id"));
      view.put("question_id", record.get("question_id"));
      view.put("verdict", record.get("verdict"));
      view.put("score", record.get("score"));
      view.put("last_at", json.iso(record.get("created_at")));
      view.put("type", question == null ? null : question.get("type"));
      view.put("content", question == null ? null : question.get("content"));
      view.put("difficulty", question == null ? null : question.get("difficulty"));
      view.put("unit_id", question == null ? null : question.get("unit_id"));
      view.put("unit_name", unit == null ? "" : unit.get("name"));
      view.put("bank_name", bank == null ? "" : bank.get("name"));
      result.add(view);
    }
    return result;
  }

  public Map<String, Object> statsMe() {
    String userId = CurrentUser.id();
    List<Map<String, Object>> records = jdbc.queryForList(
        "select question_id, verdict from practice_record where user_id = ?", userId);

    Map<String, Object> overall = new LinkedHashMap<>();
    overall.put("total", records.size());
    overall.put("correct", count(records, "CORRECT"));
    overall.put("partial", count(records, "PARTIAL"));
    overall.put("wrong", count(records, "WRONG"));
    overall.put("pending", count(records, "PENDING"));

    List<Long> questionIds = records.stream()
        .map(r -> ((Number) r.get("question_id")).longValue()).distinct().toList();
    List<Map<String, Object>> byUnit = new ArrayList<>();
    if (!questionIds.isEmpty()) {
      List<Map<String, Object>> questions = jdbc.queryForList("""
          select id, unit_id from question where id in (%s)
          """.formatted(placeholders(questionIds.size())), questionIds.toArray());
      Map<Long, Long> questionUnit = new LinkedHashMap<>();
      for (Map<String, Object> q : questions) {
        questionUnit.put(((Number) q.get("id")).longValue(),
            ((Number) q.get("unit_id")).longValue());
      }

      List<Long> unitIds = questionUnit.values().stream().distinct().toList();
      List<Map<String, Object>> units = unitIds.isEmpty() ? List.of()
          : jdbc.queryForList("""
              select id, bank_id, name from unit where id in (%s)
              """.formatted(placeholders(unitIds.size())), unitIds.toArray());
      Map<Long, Map<String, Object>> unitMap = index(units);

      List<Long> bankIds = units.stream()
          .map(u -> ((Number) u.get("bank_id")).longValue()).distinct().toList();
      List<Map<String, Object>> banks = bankIds.isEmpty() ? List.of()
          : jdbc.queryForList("""
              select id, name from bank where id in (%s)
              """.formatted(placeholders(bankIds.size())), bankIds.toArray());
      Map<Long, Map<String, Object>> bankMap = index(banks);

      Map<Long, Map<String, Object>> groups = new LinkedHashMap<>();
      for (Map<String, Object> record : records) {
        long questionId = ((Number) record.get("question_id")).longValue();
        Long unitId = questionUnit.get(questionId);
        if (unitId == null) {
          continue;
        }
        Map<String, Object> unit = unitMap.get(unitId);
        Map<String, Object> bank = unit == null ? null
            : bankMap.get(((Number) unit.get("bank_id")).longValue());
        Map<String, Object> group = groups.computeIfAbsent(unitId, key -> {
          Map<String, Object> created = new LinkedHashMap<>();
          created.put("bank_name", bank == null ? "" : bank.get("name"));
          created.put("unit_name", unit == null ? "" : unit.get("name"));
          created.put("total", 0);
          created.put("correct", 0);
          return created;
        });
        group.put("total", ((Number) group.get("total")).intValue() + 1);
        if ("CORRECT".equals(record.get("verdict"))) {
          group.put("correct", ((Number) group.get("correct")).intValue() + 1);
        }
      }
      byUnit = new ArrayList<>(groups.values());
      byUnit.sort((a, b) -> {
        int byBank = String.valueOf(a.get("bank_name")).compareTo(String.valueOf(b.get("bank_name")));
        return byBank != 0 ? byBank
            : String.valueOf(a.get("unit_name")).compareTo(String.valueOf(b.get("unit_name")));
      });
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("overall", overall);
    result.put("byUnit", byUnit);
    return result;
  }

  @Transactional
  public void selfEval(long recordId, Object rawVerdict) {
    String value = String.valueOf(rawVerdict).toUpperCase(Locale.ROOT);
    String verdict = "CORRECT".equals(value) ? "CORRECT"
        : "PARTIAL".equals(value) ? "PARTIAL" : "WRONG";
    int score = "CORRECT".equals(verdict) ? 10 : "PARTIAL".equals(verdict) ? 5 : 0;
    int updated = jdbc.update("""
        update practice_record
           set verdict = ?, judge_source = 'SELF', score = ?
         where id = ? and user_id = ? and verdict = 'PENDING'
        """, verdict, score, recordId, CurrentUser.id());
    if (updated == 0) {
      throw new ApiException(400, "记录不存在、不属于你或不处于待复核状态");
    }
  }

  private String normalizeSelected(Object raw) {
    String value = raw == null ? "" : String.valueOf(raw);
    if (raw instanceof List<?> list) {
      value = String.join("", list.stream().map(String::valueOf).toList());
    }
    char[] chars = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").toCharArray();
    Arrays.sort(chars);
    StringBuilder builder = new StringBuilder();
    for (char c : chars) {
      if (builder.indexOf(String.valueOf(c)) < 0) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private long count(List<Map<String, Object>> records, String verdict) {
    return records.stream().filter(r -> verdict.equals(r.get("verdict"))).count();
  }

  private int intValue(Object value) {
    if (value == null) {
      return 0;
    }
    try {
      return (int) Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }

  private Map<Long, Map<String, Object>> index(List<Map<String, Object>> rows) {
    Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      map.put(((Number) row.get("id")).longValue(), row);
    }
    return map;
  }
}
