package com.shuati.billing;

import com.shuati.common.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointAccountService {

  private final JdbcTemplate jdbc;

  @Transactional
  public void ensureAccount(String userId) {
    if (exists(userId)) {
      return;
    }
    Integer monthly = jdbc.queryForObject(
        "select monthly_points from plan where code = 'free'", Integer.class);
    int freePoints = monthly == null ? 0 : monthly;
    int signupBonus = signupBonusPoints();

    jdbc.update("""
        insert into point_account
          (user_id, plan_code, monthly_quota, monthly_used, bonus_balance,
           period_start, period_end, lifetime_granted, lifetime_used)
        values (?, 'free', ?, 0, ?, now(6), date_add(now(6), interval 1 month), ?, 0)
        """, userId, freePoints, signupBonus, freePoints + signupBonus);

    if (signupBonus > 0) {
      ledger(userId, signupBonus, "BONUS", signupBonus, "SIGNUP_BONUS", null, null, "新用户注册赠送");
    }
  }

  @Transactional
  public void renewPeriod(String userId) {
    ensureAccount(userId);
    Map<String, Object> account = lockedAccount(userId);

    Object expiresAt = account.get("plan_expires_at");
    if (beforeNow(expiresAt) && !"free".equals(account.get("plan_code"))) {
      jdbc.update(
          "update point_account set plan_code = 'free', plan_expires_at = null where user_id = ?",
          userId);
      account = lockedAccount(userId);
    }

    if (afterNow(account.get("period_end"))) {
      return;
    }

    Map<String, Object> plan = jdbc.queryForMap(
        "select * from plan where code = ?", account.get("plan_code"));
    int planMonthly = intValue(plan.get("monthly_points"));

    // 免费版每月额度改成「保底」：
    //   账号资产 >= planMonthly  -> 不赠送（quota 置 0）
    //   账号资产 <  planMonthly  -> 只补差额，补到刚好 planMonthly
    // 付费套餐仍是完整发放（付费额度是花钱买的权益，不做保底处理）。
    int grant;
    if ("free".equals(account.get("plan_code"))) {
      int totalBefore = balanceOf(account);
      grant = Math.max(planMonthly - totalBefore, 0);
    } else {
      grant = planMonthly;
    }
    int delta = Math.max(grant - intValue(account.get("monthly_quota")), 0);

    jdbc.update("""
        update point_account
           set monthly_quota = ?, monthly_used = 0, period_start = now(6),
               period_end = date_add(now(6), interval 1 month),
               lifetime_granted = lifetime_granted + ?
         where user_id = ?
        """, grant, delta, userId);

    if (delta > 0) {
      Map<String, Object> refreshed = account(userId);
      ledger(userId, delta, "MONTHLY", balanceOf(refreshed), "MONTHLY_GRANT",
          null, null, "周期额度重置");
    }
  }

  public List<Map<String, Object>> priceRules() {
    return jdbc.queryForList(
        "select purpose, points, unit, description from ai_price_rule order by purpose");
  }

  public int costOf(String purpose, Integer chars) {
    List<Map<String, Object>> rules = jdbc.queryForList(
        "select points, unit from ai_price_rule where purpose = ?", purpose);
    if (rules.isEmpty()) {
      return 0;
    }
    int points = intValue(rules.get(0).get("points"));
    if (points <= 0) {
      return 0;
    }
    if ("PER_10K_CHARS".equals(rules.get(0).get("unit"))) {
      int units = Math.max(1, (int) Math.ceil((chars == null ? 0 : chars) / 10_000.0));
      return points * units;
    }
    return points;
  }

  @Transactional
  public Map<String, Object> consumePoints(
      String userId, String purpose, Integer chars, String refType, String refId) {
    int cost = costOf(purpose, chars);
    if (cost <= 0) {
      Map<String, Object> free = new LinkedHashMap<>();
      free.put("ok", true);
      free.put("cost", 0);
      free.put("available", -1);
      return free;
    }

    ensureAccount(userId);
    renewPeriod(userId);
    Map<String, Object> account = lockedAccount(userId);
    int monthlyLeft = Math.max(intValue(account.get("monthly_quota"))
        - intValue(account.get("monthly_used")), 0);
    int available = monthlyLeft + intValue(account.get("bonus_balance"));
    if (cost > available) {
      Map<String, Object> fail = new LinkedHashMap<>();
      fail.put("ok", false);
      fail.put("cost", cost);
      fail.put("available", available);
      return fail;
    }

    int fromMonthly = Math.min(monthlyLeft, cost);
    int fromBonus = cost - fromMonthly;
    jdbc.update("""
        update point_account
           set monthly_used = monthly_used + ?, bonus_balance = bonus_balance - ?,
               lifetime_used = lifetime_used + ?
         where user_id = ?
        """, fromMonthly, fromBonus, cost, userId);

    Map<String, Object> refreshed = account(userId);
    int balance = balanceOf(refreshed);
    ledger(userId, -cost, fromMonthly == cost ? "MONTHLY" : "BONUS", balance,
        "AI_" + purpose, refType, refId, null);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("cost", cost);
    result.put("available", balance);
    return result;
  }

  @Transactional
  public void refundPoints(
      String userId, int points, String reason, String refType, String refId) {
    if (points <= 0) {
      return;
    }
    Map<String, Object> account = lockedAccount(userId);
    if (account.isEmpty()) {
      return;
    }
    int backMonthly = Math.min(Math.max(intValue(account.get("monthly_used")), 0), points);
    int backBonus = points - backMonthly;
    jdbc.update("""
        update point_account
           set monthly_used = monthly_used - ?, bonus_balance = bonus_balance + ?,
               lifetime_used = greatest(lifetime_used - ?, 0)
         where user_id = ?
        """, backMonthly, backBonus, points, userId);

    Map<String, Object> refreshed = account(userId);
    ledger(userId, points, backMonthly == points ? "MONTHLY" : "BONUS",
        balanceOf(refreshed), reason, refType, refId, null);
  }

  @Transactional
  public void addBonusPoints(
      String userId, int points, String reason, String refType, String refId) {
    if (points <= 0) {
      return;
    }
    ensureAccount(userId);
    jdbc.update("""
        update point_account
           set bonus_balance = bonus_balance + ?, lifetime_granted = lifetime_granted + ?
         where user_id = ?
        """, points, points, userId);
    ledger(userId, points, "BONUS", balanceOf(account(userId)), reason, refType, refId, null);
  }

  @Transactional
  public void applyPlan(String userId, String planCode, LocalDateTime expiresAt) {
    Map<String, Object> plan = jdbc.queryForMap("select * from plan where code = ?", planCode);
    ensureAccount(userId);
    Map<String, Object> account = lockedAccount(userId);
    int newQuota = intValue(plan.get("monthly_points"));
    int delta = Math.max(newQuota - intValue(account.get("monthly_quota")), 0);
    int used = Math.min(intValue(account.get("monthly_used")), newQuota);

    LocalDateTime periodEnd = expiresAt;
    LocalDateTime monthEnd = LocalDateTime.now(ZoneOffset.UTC).plusMonths(1);
    if (periodEnd == null || periodEnd.isAfter(monthEnd)) {
      periodEnd = monthEnd;
    }

    jdbc.update("""
        update point_account
           set plan_code = ?, plan_expires_at = ?, monthly_quota = ?, monthly_used = ?,
               period_start = now(6), period_end = ?,
               lifetime_granted = lifetime_granted + ?
         where user_id = ?
        """, planCode, expiresAt, newQuota, used, periodEnd, delta, userId);

    if (delta > 0) {
      ledger(userId, delta, "MONTHLY", balanceOf(account(userId)), "PLAN_CHANGE",
          null, null, "切换套餐：" + planCode);
    }
  }

  @Transactional
  public Map<String, Object> entitlements(String userId) {
    ensureAccount(userId);
    renewPeriod(userId);
    Map<String, Object> account = account(userId);
    String planCode = String.valueOf(account.getOrDefault("plan_code", "free"));
    Map<String, Object> plan = jdbc.queryForList("select * from plan where code = ?", planCode)
        .stream().findFirst().orElse(Map.of());

    int monthlyQuota = intValue(account.get("monthly_quota"));
    int monthlyUsed = intValue(account.get("monthly_used"));
    int bonus = intValue(account.get("bonus_balance"));
    int monthlyLeft = Math.max(monthlyQuota - monthlyUsed, 0);

    // 纯点数制：买过点数包的用户，展示最近购买的包名，而不是“免费版”
    String lastPackCode = account.get("last_pack_code") == null
        ? null : String.valueOf(account.get("last_pack_code"));
    String displayName = String.valueOf(plan.getOrDefault("name", "免费版"));
    if (lastPackCode != null && !lastPackCode.isBlank()) {
      displayName = jdbc.queryForList(
              "select name from point_pack where code = ?", String.class, lastPackCode)
          .stream().findFirst().orElse(displayName);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("planCode", planCode);
    result.put("planName", displayName);
    result.put("planExpiresAt", toIso(account.get("plan_expires_at")));
    result.put("monthlyQuota", monthlyQuota);
    result.put("monthlyUsed", monthlyUsed);
    result.put("monthlyLeft", monthlyLeft);
    result.put("bonusBalance", bonus);
    result.put("available", monthlyLeft + bonus);
    result.put("lifetimeUsed", intValue(account.get("lifetime_used")));
    result.put("periodStart", toIso(account.get("period_start")));
    result.put("periodEnd", toIso(account.get("period_end")));
    result.put("maxBanks", plan.get("max_banks"));
    result.put("maxQuestions", plan.get("max_questions"));
    result.put("retentionDays", plan.get("retention_days"));
    result.put("allowProModel", intValue(plan.get("allow_pro_model")) == 1);
    return result;
  }

  public List<Map<String, Object>> ledger(String userId, int limit) {
    return jdbc.queryForList("""
        select id, delta, bucket, balance_after, reason, note, created_at
          from point_ledger
         where user_id = ?
         order by created_at desc
         limit ?
        """, userId, Math.max(1, Math.min(limit, 200)));
  }

  public void assertBankQuota(String userId, Map<String, Object> entitlements) {
    Object maxBanks = entitlements.get("maxBanks");
    if (maxBanks == null) {
      return;
    }
    Integer count = jdbc.queryForObject(
        "select count(*) from bank where owner_id = ?", Integer.class, userId);
    int current = count == null ? 0 : count;
    if (current >= intValue(maxBanks)) {
      throw new ApiException(402, "当前套餐最多创建 " + intValue(maxBanks)
          + " 个自建题库，已用 " + current + " 个。升级套餐可提升上限。");
    }
  }

  public void assertQuestionQuota(String userId, Map<String, Object> entitlements, int incoming) {
    Object maxQuestions = entitlements.get("maxQuestions");
    if (maxQuestions == null) {
      return;
    }
    Integer used = jdbc.queryForObject("""
        select count(*) from question q
          join unit u on u.id = q.unit_id
          join bank b on b.id = u.bank_id
         where b.owner_id = ?
        """, Integer.class, userId);
    int current = used == null ? 0 : used;
    if (current + incoming > intValue(maxQuestions)) {
      throw new ApiException(402, "当前套餐自建题量上限 " + intValue(maxQuestions)
          + " 题，已用 " + current + " 题，本次将新增 " + incoming + " 题。升级套餐可提升上限。");
    }
  }

  private boolean exists(String userId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from point_account where user_id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  private Map<String, Object> account(String userId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select * from point_account where user_id = ?", userId);
    return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
  }

  private Map<String, Object> lockedAccount(String userId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select * from point_account where user_id = ? for update", userId);
    return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
  }

  private int balanceOf(Map<String, Object> account) {
    return Math.max(intValue(account.get("monthly_quota"))
        - intValue(account.get("monthly_used")), 0) + intValue(account.get("bonus_balance"));
  }

  private int signupBonusPoints() {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select value from billing_config where `key` = 'signup_bonus_points'");
    if (rows.isEmpty()) {
      return 0;
    }
    try {
      return Math.max(0,
          Integer.parseInt(String.valueOf(rows.get(0).get("value")).trim().replace("\"", "")));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void ledger(
      String userId, int delta, String bucket, int balanceAfter, String reason,
      String refType, String refId, String note) {
    jdbc.update("""
        insert into point_ledger
          (user_id, delta, bucket, balance_after, reason, ref_type, ref_id, note)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """, userId, delta, bucket, balanceAfter, reason, refType, refId, note);
  }

  private int intValue(Object value) {
    if (value == null) {
      return 0;
    }
    if (value instanceof Boolean bool) {
      return bool ? 1 : 0;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return (int) Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private Object toIso(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().toString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toInstant(ZoneOffset.UTC).toString();
    }
    return value;
  }

  private boolean beforeNow(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().isBefore(Instant.now());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.isBefore(LocalDateTime.now(ZoneOffset.UTC));
    }
    return false;
  }

  private boolean afterNow(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().isAfter(Instant.now());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }
    return false;
  }
}

