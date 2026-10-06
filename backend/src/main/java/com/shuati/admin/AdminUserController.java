package com.shuati.admin;

import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.Json;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminUserController {

  private final JdbcTemplate jdbc;
  private final Json json;
  private final PasswordEncoder passwordEncoder;
  private final PointAccountService points;

  @GetMapping("/api/admin/users")
  public ApiResponse<List<Map<String, Object>>> users() {
    return ApiResponse.ok(json.normalize(jdbc.queryForList("""
        select id, email, nickname, role, status, created_at, last_login_at
          from profiles order by created_at asc
        """), Set.of()));
  }

  @PatchMapping("/api/admin/users/{id}")
  public ApiResponse<Void> updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
    if (body.get("role") != null) {
      String role = String.valueOf(body.get("role")).toUpperCase();
      if (!List.of("ADMIN", "USER").contains(role)) {
        throw new ApiException(400, "角色不正确");
      }
      jdbc.update("update profiles set role = ? where id = ?", role, id);
    }
    if (body.get("status") != null) {
      String status = String.valueOf(body.get("status")).toUpperCase();
      if (!List.of("ENABLED", "DISABLED").contains(status)) {
        throw new ApiException(400, "状态不正确");
      }
      jdbc.update("update profiles set status = ? where id = ?", status, id);
    }
    if (body.get("nickname") != null) {
      jdbc.update("update profiles set nickname = ? where id = ?",
          String.valueOf(body.get("nickname")), id);
    }
    if (body.get("password") != null) {
      String password = String.valueOf(body.get("password"));
      if (password.length() < 6) {
        throw new ApiException(400, "密码至少 6 位");
      }
      jdbc.update("update profiles set password_hash = ? where id = ?",
          passwordEncoder.encode(password), id);
    }
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/admin/users/{id}/points")
  public ApiResponse<Map<String, Object>> userPoints(@PathVariable String id) {
    return ApiResponse.ok(points.entitlements(id));
  }

  @PostMapping("/api/admin/users/{id}/points")
  public ApiResponse<Void> adjustPoints(@PathVariable String id, @RequestBody Map<String, Object> body) {
    String action = String.valueOf(body.getOrDefault("action", "")).toUpperCase();
    if ("GRANT".equals(action)) {
      int delta = (int) Math.round(Double.parseDouble(String.valueOf(body.getOrDefault("points", 0))));
      if (delta == 0) {
        throw new ApiException(400, "请输入非零点数");
      }
      if (delta > 0) {
        points.addBonusPoints(id, delta, "ADMIN_ADJUST", "admin", null);
      } else {
        points.consumePoints(id, "TEST", null, "admin", null);
        jdbc.update("""
            update point_account
               set bonus_balance = greatest(bonus_balance - ?, 0)
             where user_id = ?
            """, Math.abs(delta), id);
      }
      return ApiResponse.ok(null);
    }
    if ("SET_PLAN".equals(action)) {
      String planCode = String.valueOf(body.getOrDefault("planCode", "")).trim();
      if (planCode.isEmpty()) {
        throw new ApiException(400, "缺少套餐编码");
      }
      int days = body.get("days") == null ? 30
          : (int) Double.parseDouble(String.valueOf(body.get("days")));
      LocalDateTime expires = "free".equals(planCode) ? null
          : LocalDateTime.now(ZoneOffset.UTC).plusDays(days);
      points.applyPlan(id, planCode, expires);
      return ApiResponse.ok(null);
    }
    throw new ApiException(400, "不支持的操作");
  }

  @GetMapping("/api/admin/appeals")
  public ApiResponse<List<Map<String, Object>>> appeals(
      @RequestParam(defaultValue = "PENDING") String status) {
    List<Map<String, Object>> appeals = json.normalize(jdbc.queryForList("""
        select * from appeal order by created_at desc
        """), Set.of());
    if (!"ALL".equalsIgnoreCase(status)) {
      appeals = appeals.stream()
          .filter(a -> status.equalsIgnoreCase(String.valueOf(a.get("status"))))
          .toList();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> appeal : appeals) {
      long recordId = ((Number) appeal.get("practice_record_id")).longValue();
      List<Map<String, Object>> records = jdbc.queryForList(
          "select * from practice_record where id = ?", recordId);
      Map<String, Object> record = records.isEmpty() ? Map.of() : records.get(0);
      List<Map<String, Object>> profiles = jdbc.queryForList(
          "select id, email, nickname from profiles where id = ?", appeal.get("user_id"));
      Map<String, Object> profile = profiles.isEmpty() ? Map.of() : profiles.get(0);
      List<Map<String, Object>> questions = record.isEmpty() ? List.of()
          : jdbc.queryForList(
              "select id, content, type, answer from question where id = ?", record.get("question_id"));
      Map<String, Object> question = questions.isEmpty() ? Map.of() : questions.get(0);

      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", appeal.get("id"));
      view.put("reason", appeal.get("reason"));
      view.put("status", appeal.get("status"));
      view.put("admin_note", appeal.get("admin_note"));
      view.put("created_at", json.iso(appeal.get("created_at")));
      view.put("resolved_at", json.iso(appeal.get("resolved_at")));
      view.put("user_email", profile.getOrDefault("email", ""));
      view.put("user_nickname", profile.getOrDefault("nickname", ""));
      view.put("user_answer", record.getOrDefault("user_answer", ""));
      view.put("verdict", record.getOrDefault("verdict", ""));
      view.put("score", record.get("score"));
      view.put("ai_feedback", json.parse(record.get("ai_feedback") == null
          ? null : String.valueOf(record.get("ai_feedback"))));
      view.put("question_content", question.getOrDefault("content", ""));
      view.put("question_type", question.getOrDefault("type", ""));
      view.put("question_answer", question.getOrDefault("answer", ""));
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  @PostMapping("/api/admin/appeals/{id}/resolve")
  public ApiResponse<Void> resolveAppeal(@PathVariable long id, @RequestBody Map<String, Object> body) {
    String status = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
    if (!List.of("RESOLVED", "REJECTED").contains(status)) {
      throw new ApiException(400, "status 只能是 RESOLVED / REJECTED");
    }
    jdbc.update("""
        update appeal set status = ?, admin_note = ?, resolved_at = now(6) where id = ?
        """, status, body.get("adminNote") == null ? null : String.valueOf(body.get("adminNote")), id);
    return ApiResponse.ok(null);
  }
}
