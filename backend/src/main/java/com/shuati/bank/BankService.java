package com.shuati.bank;

import com.shuati.common.ApiException;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankService {

  private static final Set<String> QUESTION_JSON_COLUMNS = Set.of("options", "images", "tags");

  private final JdbcTemplate jdbc;
  private final Json json;

  public List<Map<String, Object>> listBanks() {
    // 游客没有 userId，只会看到公共题库（owner_id 为空）
    String userId = CurrentUser.idOrNull();
    boolean admin = CurrentUser.isAdmin();

    List<Map<String, Object>> banks = jdbc.queryForList("""
        select id, name, description, is_default, created_at, owner_id
          from bank
         order by is_default desc, id asc
        """).stream()
        .filter(b -> admin || b.get("owner_id") == null
            || (userId != null && userId.equals(String.valueOf(b.get("owner_id")))))
        .map(b -> json.normalize(b, Set.of()))
        .toList();

    List<Map<String, Object>> units = jdbc.queryForList(
        "select id, bank_id, name, sort from unit");
    List<Map<String, Object>> questions = jdbc.queryForList(
        "select id, unit_id, status from question");
    Set<Long> answered = answeredQuestionIds(userId);

    Set<Long> visibleBankIds = new HashSet<>();
    for (Map<String, Object> bank : banks) {
      visibleBankIds.add(((Number) bank.get("id")).longValue());
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> bank : banks) {
      long bankId = ((Number) bank.get("id")).longValue();
      List<Long> unitIds = units.stream()
          .filter(u -> ((Number) u.get("bank_id")).longValue() == bankId)
          .map(u -> ((Number) u.get("id")).longValue())
          .toList();

      List<Map<String, Object>> bankQuestions = questions.stream()
          .filter(q -> unitIds.contains(((Number) q.get("unit_id")).longValue()))
          .toList();

      long onCount = bankQuestions.stream().filter(this::isOn).count();
      long answeredCount = bankQuestions.stream()
          .filter(this::isOn)
          .filter(q -> answered.contains(((Number) q.get("id")).longValue()))
          .count();

      Map<String, Object> view = new LinkedHashMap<>(bank);
      view.put("is_public", bank.get("owner_id") == null);
      view.put("unit_count", unitIds.size());
      view.put("question_count", onCount);
      view.put("answered_count", answeredCount);
      result.add(view);
    }
    return result;
  }

  public List<Map<String, Object>> listUnits(long bankId) {
    requireBankAccess(bankId);
    String userId = CurrentUser.idOrNull();

    List<Map<String, Object>> units = jdbc.queryForList("""
        select id, bank_id, name, sort, created_at
          from unit where bank_id = ? order by sort asc, id asc
        """, bankId).stream().map(u -> json.normalize(u, Set.of())).toList();

    List<Map<String, Object>> questions = jdbc.queryForList(
        "select id, unit_id, status from question");
    Set<Long> answered = answeredQuestionIds(userId);

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> unit : units) {
      long unitId = ((Number) unit.get("id")).longValue();
      List<Map<String, Object>> unitQuestions = questions.stream()
          .filter(q -> ((Number) q.get("unit_id")).longValue() == unitId)
          .toList();
      Map<String, Object> view = new LinkedHashMap<>(unit);
      view.put("question_count", unitQuestions.stream().filter(this::isOn).count());
      view.put("answered_count", unitQuestions.stream()
          .filter(this::isOn)
          .filter(q -> answered.contains(((Number) q.get("id")).longValue()))
          .count());
      result.add(view);
    }
    return result;
  }

  public List<Map<String, Object>> listBankQuestions(long bankId, String mode) {
    requireBankAccess(bankId);
    List<Long> unitIds = jdbc.queryForList(
        "select id from unit where bank_id = ?", Long.class, bankId);
    return questionsByUnitIds(unitIds, mode);
  }

  public List<Map<String, Object>> listUnitQuestions(long unitId, String mode) {
    Map<String, Object> unit = jdbc.queryForMap(
        "select id, bank_id from unit where id = ?", unitId);
    requireBankAccess(((Number) unit.get("bank_id")).longValue());
    return questionsByUnitIds(List.of(unitId), mode);
  }

  public List<Map<String, Object>> listAllQuestions(String mode) {
    List<Long> unitIds = visibleUnitIds();
    return questionsByUnitIds(unitIds, mode);
  }

  /**
   * 背题模式按题读取答案。答案不在题目列表里批量下发，避免普通刷题时提前泄题。
   */
  public Map<String, Object> questionAnswer(long questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.answer, q.status, u.bank_id
          from question q
          join unit u on u.id = q.unit_id
         where q.id = ?
        """, questionId);
    if (rows.isEmpty() || !"ON".equals(rows.get(0).get("status"))) {
      throw new ApiException(404, "题目不存在或已下架");
    }
    requireBankAccess(((Number) rows.get(0).get("bank_id")).longValue());
    return Map.of("answer", rows.get(0).get("answer"));
  }

  /**
   * 问 AI 必须使用服务端读取的题目上下文，避免客户端篡改题干或越过题库权限。
   */
  public Map<String, Object> questionForAi(long questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.content, q.options, q.answer, q.explanation, q.status, u.bank_id
          from question q
          join unit u on u.id = q.unit_id
         where q.id = ?
        """, questionId);
    if (rows.isEmpty() || !"ON".equals(rows.get(0).get("status"))) {
      throw new ApiException(404, "题目不存在或已下架");
    }
    Map<String, Object> question = rows.get(0);
    requireBankAccess(((Number) question.get("bank_id")).longValue());
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("content", question.get("content"));
    result.put("options", question.get("options"));
    result.put("answer", question.get("answer"));
    result.put("explanation", question.get("explanation"));
    return result;
  }

  public Map<String, Object> bankProgress(long bankId) {
    requireBankAccess(bankId);
    List<Long> unitIds = jdbc.queryForList(
        "select id from unit where bank_id = ?", Long.class, bankId);
    return progressForUnitIds(unitIds);
  }

  public Map<String, Object> unitProgress(long unitId) {
    Map<String, Object> unit = jdbc.queryForMap(
        "select id, bank_id from unit where id = ?", unitId);
    requireBankAccess(((Number) unit.get("bank_id")).longValue());
    return progressForUnitIds(List.of(unitId));
  }

  public Map<String, Object> allProgress() {
    return progressForUnitIds(visibleUnitIds());
  }

  /**
   * 读练习光标：这个账号在某个范围里最后停在哪道题。
   *
   * <p>返回 null 表示还没记过位置 —— 客户端这时回退到老的「第一道没答过的题」规则，
   * 所以老用户的历史进度不会因为这次改造丢失。
   */
  public Long cursor(String scopeType, long scopeId) {
    String type = normalizeScope(scopeType);
    requireScopeAccess(type, scopeId);
    List<Long> ids = jdbc.queryForList("""
        select question_id from practice_cursor
         where user_id = ? and scope_type = ? and scope_id = ?
        """, Long.class, CurrentUser.id(), type, scopeId);
    return ids.isEmpty() ? null : ids.get(0);
  }

  /**
   * 写/清练习光标。
   *
   * <p>`questionId` 传 null 表示清空（一轮做完时用），这样下次进入回到「全部答完就从头开始」的老规则。
   * 写入用 `on duplicate key update`，天然幂等：客户端每切一题调一次也不会写重复行。
   */
  public void saveCursor(String scopeType, long scopeId, Long questionId) {
    String type = normalizeScope(scopeType);
    requireScopeAccess(type, scopeId);
    String userId = CurrentUser.id();
    if (questionId == null) {
      jdbc.update("""
          delete from practice_cursor
           where user_id = ? and scope_type = ? and scope_id = ?
          """, userId, type, scopeId);
      return;
    }
    Integer exists = jdbc.queryForObject(
        "select count(*) from question where id = ?", Integer.class, questionId);
    if (exists == null || exists == 0) {
      throw new ApiException(404, "题目不存在");
    }
    jdbc.update("""
        insert into practice_cursor (user_id, scope_type, scope_id, question_id)
        values (?, ?, ?, ?)
        on duplicate key update question_id = values(question_id)
        """, userId, type, scopeId, questionId);
  }

  private String normalizeScope(String scopeType) {
    String type = scopeType == null ? "" : scopeType.toUpperCase();
    if (!List.of("ALL", "BANK", "UNIT").contains(type)) {
      throw new ApiException(400, "练习范围不正确");
    }
    return type;
  }

  /** 光标只能落在看得见的范围里，别让参数把光标写到别人的题库上 */
  private void requireScopeAccess(String scopeType, long scopeId) {
    switch (scopeType) {
      case "BANK" -> {
        if (scopeId == 0) {
          throw new ApiException(400, "缺少题库 id");
        }
        requireBankAccess(scopeId);
      }
      case "UNIT" -> {
        if (scopeId == 0) {
          throw new ApiException(400, "缺少单元 id");
        }
        List<Long> bankIds = jdbc.queryForList(
            "select bank_id from unit where id = ?", Long.class, scopeId);
        if (bankIds.isEmpty()) {
          throw new ApiException(404, "单元不存在");
        }
        requireBankAccess(bankIds.get(0));
      }
      default -> {
        // ALL：所有可见题库的合集，无需额外校验
      }
    }
  }

  private List<Map<String, Object>> questionsByUnitIds(List<Long> unitIds, String mode) {
    if (unitIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", unitIds.stream().map(id -> "?").toList());
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, unit_id, type, content, options, difficulty, images, tags, status, explanation
          from question
         where status = 'ON' and unit_id in (%s)
         order by id asc
        """.formatted(placeholders), unitIds.toArray());
    List<Map<String, Object>> result = json.normalize(rows, QUESTION_JSON_COLUMNS);
    if ("random".equals(mode)) {
      List<Map<String, Object>> shuffled = new ArrayList<>(result);
      java.util.Collections.shuffle(shuffled);
      return shuffled;
    }
    return result;
  }

  private Map<String, Object> progressForUnitIds(List<Long> unitIds) {
    if (unitIds.isEmpty()) {
      return Map.of("total", 0, "answered", 0, "correct", 0, "answeredIds", List.of());
    }
    String placeholders = String.join(",", unitIds.stream().map(id -> "?").toList());
    List<Long> questionIds = jdbc.queryForList("""
        select id from question
         where status = 'ON' and unit_id in (%s)
        """.formatted(placeholders), Long.class, unitIds.toArray());

    Set<Long> questionIdSet = new HashSet<>(questionIds);
    List<Map<String, Object>> records = jdbc.queryForList("""
        select question_id, verdict, created_at
          from practice_record
         where user_id = ?
         order by created_at desc
        """, CurrentUser.id());

    Map<Long, String> latest = new LinkedHashMap<>();
    for (Map<String, Object> record : records) {
      long questionId = ((Number) record.get("question_id")).longValue();
      if (questionIdSet.contains(questionId) && !latest.containsKey(questionId)) {
        latest.put(questionId, String.valueOf(record.get("verdict")));
      }
    }
    long correct = latest.values().stream().filter("CORRECT"::equals).count();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", questionIds.size());
    result.put("answered", latest.size());
    result.put("correct", correct);
    result.put("answeredIds", new ArrayList<>(latest.keySet()));
    return result;
  }

  private Set<Long> answeredQuestionIds(String userId) {
    if (userId == null) {
      return Set.of();
    }
    return new HashSet<>(jdbc.queryForList(
        "select distinct question_id from practice_record where user_id = ?",
        Long.class, userId));
  }

  private List<Long> visibleUnitIds() {
    String userId = CurrentUser.id();
    boolean admin = CurrentUser.isAdmin();
    List<Long> bankIds = jdbc.queryForList(
        "select id from bank where ? = 1 or owner_id is null or owner_id = ?",
        Long.class, admin ? 1 : 0, userId);
    if (bankIds.isEmpty()) {
      return List.of();
    }
    return jdbc.queryForList("""
        select id from unit where bank_id in (%s)
        """.formatted(String.join(",", bankIds.stream().map(id -> "?").toList())),
        Long.class, bankIds.toArray());
  }

  private void requireBankAccess(long bankId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select id, owner_id from bank where id = ?", bankId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "题库不存在");
    }
    Object ownerId = rows.get(0).get("owner_id");
    if (CurrentUser.isAdmin() || ownerId == null) {
      return;
    }
    // 私有题库只有所有者本人能访问，游客一律拒绝
    String userId = CurrentUser.idOrNull();
    if (userId == null || !String.valueOf(ownerId).equals(userId)) {
      throw new ApiException(403, "无权访问该题库");
    }
  }

  private boolean isOn(Map<String, Object> question) {
    return "ON".equals(question.get("status"));
  }
}
