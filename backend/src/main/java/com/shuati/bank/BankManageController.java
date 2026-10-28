package com.shuati.bank;

import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.ClientInfo;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import com.shuati.common.QuestionContent;
import com.shuati.email.EmailDispatchService;
import com.shuati.email.EmailPurpose;
import com.shuati.email.EmailVerificationService;
import com.shuati.question.QuestionTypeSupport;
import com.shuati.rate.RateLimit;
import com.shuati.user.Profile;
import com.shuati.user.ProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
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
  private final ProfileRepository profiles;
  private final EmailVerificationService emails;
  private final EmailDispatchService emailDispatch;
  private final BankWordExportService wordExport;

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
    // 新建题库受平台统一的题库数量上限约束（往已有题库加题不占）
    points.assertBankQuota(userId);
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

  /** 导出当前账号有维护权限的完整题库，格式为自动排版的 Word 2007+ 文档。 */
  @GetMapping(value = "/api/manage/banks/{id}/export.docx",
      produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  @RateLimit(name = "bank:export-word", limit = 5, windowSeconds = 60,
      scope = RateLimit.Scope.USER)
  public ResponseEntity<byte[]> exportWord(@PathVariable long id) {
    requireManage(id);
    BankWordExportService.ExportFile file = wordExport.export(id);
    ContentDisposition disposition = ContentDisposition.attachment()
        .filename(file.fileName(), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .contentLength(file.content().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.content());
  }

  /**
   * 删题库要过邮箱验证码：把 6 位码发到当前账号的邮箱。
   *
   * <p>用单独的 DELETE_BANK 用途，键里带 purpose，所以注册/重置密码的验证码不能拿来删题库。
   */
  @PostMapping("/api/manage/banks/{id}/delete-code")
  @RateLimit(name = "bank:delete-code:ip", limit = 30,
      windowSeconds = 3600, scope = RateLimit.Scope.IP)
  public ApiResponse<EmailVerificationService.SendResult> sendDeleteBankCode(
      @PathVariable long id, HttpServletRequest http) {
    requireManage(id);
    String email = currentEmail();
    EmailVerificationService.PreparedCode prepared = emails.prepareCode(
        email, ClientInfo.ip(http), ClientInfo.device(http), EmailPurpose.DELETE_BANK);
    emailDispatch.send(prepared);
    return ApiResponse.ok(prepared.result());
  }

  /**
   * 删除题库：单元、题目、以及这些题目留下的练习记录一并删掉，删完不可恢复。
   *
   * <p>question.unit_id 是 RESTRICT，所以必须先删题目再删单元；practice_session 的
   * scope_id 没有外键约束，也要显式清掉，否则会留下指向已删题库的续做记录。
   */
  @PostMapping("/api/manage/banks/{id}/delete")
  @Transactional
  public ApiResponse<Void> deleteBank(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    requireManage(id);
    emails.verifyCode(currentEmail(), string(body.get("code")), EmailPurpose.DELETE_BANK);
    jdbc.update(
        "delete from question where unit_id in (select id from unit where bank_id = ?)", id);
    jdbc.update("""
        delete from practice_session
         where (scope_type = 'UNIT' and scope_id in (select id from unit where bank_id = ?))
            or (scope_type = 'BANK' and scope_id = ?)
        """, id, id);
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
      @PathVariable long id,
      @RequestParam(defaultValue = "ALL") String status,
      @RequestParam(defaultValue = "") String q) {
    requireManage(id);
    String filter = status.toUpperCase();
    String keyword = likeKeyword(q);
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.* from question q join unit u on u.id = q.unit_id
         where u.bank_id = ? and (? not in ('ON', 'OFF') or q.status = ?)
           and (? = '' or q.content like concat('%', ?, '%'))
         order by q.id desc limit 500
        """, id, filter, filter, keyword, keyword);
    return ApiResponse.ok(json.normalize(rows, QUESTION_JSON));
  }

  /**
   * 题库内检索的关键词：去空白 + 转义 LIKE 通配符。
   *
   * <p>不转义的话，用户输入一个 `%` 就会把整个题库捞回来，看着像检索失效；
   * 空串表示「不筛选」，由 SQL 里的 {@code ? = ''} 短路。
   */
  private String likeKeyword(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
    String answer = QuestionTypeSupport.normalizeAnswer(type, body.get("answer"));
    Object options = QuestionTypeSupport.normalizeOptions(type, body.get("options"));
    if (content.isEmpty()) {
      throw new ApiException(400, "题干不能为空");
    }
    validateQuestion(type, options, answer);

    long targetUnitId = unitId;
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into question
            (unit_id, type, content, content_hash, options, answer, key_points,
             difficulty, explanation, tags, status)
          values (?, ?, ?, ?, cast(? as json), ?, cast(? as json), ?, ?,
                  cast(? as json), 'ON')
          """, new String[]{"id"});
      ps.setLong(1, targetUnitId);
      ps.setString(2, type);
      ps.setString(3, content);
      ps.setString(4, QuestionContent.hash(content));
      ps.setString(5, writeJson(options));
      ps.setString(6, answer);
      ps.setString(7, QuestionTypeSupport.isTrueFalse(type)
          ? null : writeJson(body.get("keyPoints")));
      ps.setString(8, normalizeDifficulty(body.get("difficulty")));
      ps.setString(9, body.get("explanation") == null ? null : string(body.get("explanation")));
      ps.setString(10, writeJson(body.get("tags") == null ? List.of() : body.get("tags")));
      return ps;
    }, keyHolder);
    long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    return ApiResponse.ok(Map.of("id", id));
  }

  @PutMapping("/api/manage/questions/{id}")
  public ApiResponse<Void> updateQuestion(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    QuestionScope scope = questionScope(id);
    requireManage(scope.bankId());
    String type = string(body.get("type")).toUpperCase();
    String content = string(body.get("content")).trim();
    String answer = QuestionTypeSupport.normalizeAnswer(type, body.get("answer"));
    Object options = QuestionTypeSupport.normalizeOptions(type, body.get("options"));
    if (content.isEmpty()) {
      throw new ApiException(400, "题干不能为空");
    }
    validateQuestion(type, options, answer);
    long unitId = resolveUnitId(body, scope);
    jdbc.update("""
        update question
           set unit_id = ?, type = ?, content = ?, content_hash = ?,
               options = cast(? as json), answer = ?, key_points = cast(? as json),
               difficulty = ?, explanation = ?, tags = cast(? as json)
         where id = ?
        """, unitId, type, content, QuestionContent.hash(content),
        writeJson(options), answer,
        QuestionTypeSupport.isTrueFalse(type) ? null : writeJson(body.get("keyPoints")),
        normalizeDifficulty(body.get("difficulty")),
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

  /** 题目的归属：题库 + 当前所在单元（换单元和权限判断都要用） */
  private record QuestionScope(long bankId, long unitId) {}

  private QuestionScope questionScope(long questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select u.bank_id, q.unit_id from question q join unit u on u.id = q.unit_id
         where q.id = ?
        """, questionId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "题目不存在");
    }
    Map<String, Object> row = rows.get(0);
    return new QuestionScope(longValue(row.get("bank_id")), longValue(row.get("unit_id")));
  }

  private long bankIdOfQuestion(long questionId) {
    return questionScope(questionId).bankId();
  }

  /**
   * 编辑时允许换单元。
   *
   * <p>前端表单里的「所属单元」下拉框就是这个字段：选中具体单元传单元 id，
   * 选「默认单元（未分类）」传 null。PUT 只有这一个调用方，所以
   * null = 用户主动选了未分类（落到该题库的兜底单元）。
   * 没有这个 key 时保持原单元，避免老客户端把题目莫名其妙挪走。
   */
  private long resolveUnitId(Map<String, Object> body, QuestionScope scope) {
    if (!body.containsKey("unitId")) {
      return scope.unitId();
    }
    long requested = longValue(body.get("unitId"));
    if (requested == 0) {
      return defaultUnitId(scope.bankId());
    }
    if (requested == scope.unitId()) {
      return requested;
    }
    if (bankIdOfUnit(requested) != scope.bankId()) {
      throw new ApiException(400, "题目只能移到同一题库下的单元");
    }
    return requested;
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /** 当前账号的邮箱，删题库的验证码发到这里 */
  private String currentEmail() {
    Profile profile = profiles.findById(CurrentUser.id())
        .orElseThrow(() -> new ApiException(400, "账号不存在"));
    String email = profile.getEmail();
    if (email == null || email.isBlank()) {
      throw new ApiException(400, "当前账号没有可用邮箱，无法发送验证码");
    }
    return email;
  }

  private void validateQuestion(String type, Object options, String answer) {
    if (!QuestionTypeSupport.isValidType(type)) {
      throw new ApiException(400, "题型不正确");
    }
    if (answer.isEmpty()) {
      throw new ApiException(400, "答案不能为空");
    }
    if (QuestionTypeSupport.isChoiceType(type)
        && !(options instanceof List<?> list && !list.isEmpty())) {
      throw new ApiException(400, "选择题必须提供选项");
    }
    if (QuestionTypeSupport.SINGLE.equals(type) && answer.length() != 1) {
      throw new ApiException(400, "单选题答案只能是一个选项");
    }
    // 简答题的评分要点是可选的：留空时由 AI 判分时自行提炼（见 PromptTemplates.ESSAY_SYSTEM）
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
