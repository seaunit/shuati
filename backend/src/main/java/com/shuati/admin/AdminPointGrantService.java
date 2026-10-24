package com.shuati.admin;

import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPointGrantService {

  private static final int MAX_GRANT_POINTS = 1_000_000;

  private final JdbcTemplate jdbc;
  private final PointAccountService points;

  public Map<String, Object> lookupByEmail(String adminId, String rawEmail) {
    requireAdmin(adminId);
    String email = normalizeEmail(rawEmail);
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, email, nickname, status, created_at
          from profiles
         where email = ?
         limit 1
        """, email);
    if (rows.isEmpty()) {
      throw new ApiException(404, "账号不存在");
    }
    Map<String, Object> profile = rows.get(0);
    String userId = String.valueOf(profile.get("id"));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userId", userId);
    result.put("emailMasked",
        maskEmail(String.valueOf(profile.get("email")).toLowerCase(Locale.ROOT)));
    result.put("nickname", profile.get("nickname"));
    result.put("status", profile.get("status"));
    result.put("createdAt", profile.get("created_at"));
    result.put("entitlements", points.entitlements(userId));
    result.put("recentLedger", points.ledger(userId, 20));
    return result;
  }

  /**
   * 管理员赠点。先按固定顺序锁管理员与目标账号，再检查幂等键，
   * 防止并发重复请求或管理员互赠时形成锁顺序环。
   */
  @Transactional
  public Map<String, Object> grant(
      String adminId, String targetUserId, Object rawPoints, String rawReason,
      String rawIdempotencyKey) {
    int grantPoints = positiveInteger(rawPoints);
    String reason = normalizeReason(rawReason);
    String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
    lockProfiles(adminId, targetUserId);

    Map<String, Object> admin = requireAdmin(adminId);
    Map<String, Object> target = requireGrantTarget(targetUserId);

    Map<String, Object> existing = findExistingGrant(adminId, idempotencyKey);
    if (!existing.isEmpty()) {
      ensureSamePayload(existing, targetUserId, grantPoints, reason);
      return grantResult(existing, true);
    }

    points.ensureAccount(targetUserId);
    Map<String, Object> account = lockedAccount(targetUserId);
    int bonusBefore = intValue(account.get("bonus_balance"));
    if (bonusBefore > Integer.MAX_VALUE - grantPoints) {
      throw new ApiException(409, "账号点数余额已达上限，暂时无法继续赠送");
    }

    jdbc.update("""
        update point_account
           set bonus_balance = bonus_balance + ?,
               lifetime_granted = lifetime_granted + ?
         where user_id = ?
        """, grantPoints, grantPoints, targetUserId);

    int balanceAfter = monthlyLeft(account) + bonusBefore + grantPoints;
    int updated = jdbc.queryForObject("""
        select bonus_balance from point_account where user_id = ?
        """, Integer.class, targetUserId);
    if (updated != bonusBefore + grantPoints) {
      throw new ApiException(409, "点数余额更新冲突，请刷新后重试");
    }

    String grantId = UUID.randomUUID().toString();
    KeyHolder ledgerKey = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into point_ledger
            (user_id, delta, bucket, balance_after, reason, ref_type, ref_id, note)
          values (?, ?, 'BONUS', ?, 'ADMIN_GRANT', 'admin_point_grant', ?, ?)
          """, new String[]{"id"});
      ps.setString(1, targetUserId);
      ps.setInt(2, grantPoints);
      ps.setInt(3, balanceAfter);
      ps.setString(4, grantId);
      ps.setString(5, reason);
      return ps;
    }, ledgerKey);
    long ledgerId = ledgerKey.getKey() == null ? 0L : ledgerKey.getKey().longValue();

    jdbc.update("""
        insert into admin_point_grant
          (id, admin_id, admin_email, user_id, idempotency_key, points, reason, ledger_id)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """, grantId, adminId, String.valueOf(admin.get("email")), targetUserId,
        idempotencyKey, grantPoints, reason, ledgerId);

    log.info("管理员赠点成功 adminId={} targetUserId={} points={} grantId={}",
        adminId, targetUserId, grantPoints, grantId);
    Map<String, Object> created = new LinkedHashMap<>();
    created.put("id", grantId);
    created.put("user_id", targetUserId);
    created.put("points", grantPoints);
    created.put("reason", reason);
    created.put("balance_after", balanceAfter);
    created.put("created_at", jdbc.queryForObject(
        "select created_at from admin_point_grant where id = ?", Object.class, grantId));
    return grantResult(created, false);
  }

  private void lockProfiles(String adminId, String targetUserId) {
    List<String> ids = java.util.stream.Stream.of(adminId, targetUserId)
        .distinct()
        .sorted()
        .toList();
    if (ids.size() == 1) {
      jdbc.queryForList("select id from profiles where id = ? for update", ids.get(0));
    } else {
      jdbc.queryForList("""
          select id from profiles where id in (?, ?) order by id for update
          """, ids.get(0), ids.get(1));
    }
  }

  private Map<String, Object> requireAdmin(String adminId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, email, role, status from profiles where id = ?
        """, adminId);
    if (rows.isEmpty() || !"ADMIN".equals(rows.get(0).get("role"))
        || !"ENABLED".equals(rows.get(0).get("status"))) {
      throw new ApiException(403, "仅管理员可执行此操作");
    }
    return rows.get(0);
  }

  private Map<String, Object> requireGrantTarget(String userId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, email, nickname, status from profiles where id = ?
        """, userId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "账号不存在");
    }
    if (!"ENABLED".equals(rows.get(0).get("status"))) {
      throw new ApiException(409, "该账号当前不可用，不能赠送点数");
    }
    return rows.get(0);
  }

  private Map<String, Object> findExistingGrant(String adminId, String idempotencyKey) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select g.id, g.user_id, g.points, g.reason, g.created_at,
               l.balance_after
          from admin_point_grant g
          join point_ledger l on l.id = g.ledger_id
         where g.admin_id = ? and g.idempotency_key = ?
         limit 1
        """, adminId, idempotencyKey);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  private void ensureSamePayload(
      Map<String, Object> existing, String targetUserId, int points, String reason) {
    boolean same = String.valueOf(existing.get("user_id")).equals(targetUserId)
        && intValue(existing.get("points")) == points
        && String.valueOf(existing.get("reason")).equals(reason);
    if (!same) {
      throw new ApiException(409, "幂等键已被另一笔不同参数的赠点使用");
    }
  }

  private Map<String, Object> grantResult(Map<String, Object> grant, boolean reused) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("grantId", grant.get("id"));
    result.put("targetUserId", grant.get("user_id"));
    result.put("points", intValue(grant.get("points")));
    result.put("balanceAfter", intValue(grant.get("balance_after")));
    result.put("reused", reused);
    result.put("createdAt", grant.get("created_at"));
    return result;
  }

  private Map<String, Object> lockedAccount(String userId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select * from point_account where user_id = ? for update", userId);
    if (rows.isEmpty()) {
      throw new ApiException(409, "点数账户不存在，请稍后重试");
    }
    return rows.get(0);
  }

  private String normalizeEmail(String rawEmail) {
    String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    if (email.isEmpty() || email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new ApiException(400, "请输入正确的账号邮箱");
    }
    return email;
  }

  private int positiveInteger(Object rawPoints) {
    long value;
    if (rawPoints instanceof Byte || rawPoints instanceof Short
        || rawPoints instanceof Integer || rawPoints instanceof Long) {
      value = ((Number) rawPoints).longValue();
    } else if (rawPoints instanceof BigInteger bigInteger) {
      if (bigInteger.bitLength() > 63) {
        throw new ApiException(400, "赠送点数必须是 1 至 1000000 的整数");
      }
      value = bigInteger.longValue();
    } else {
      throw new ApiException(400, "赠送点数必须是 1 至 1000000 的整数");
    }
    if (value < 1 || value > MAX_GRANT_POINTS) {
      throw new ApiException(400, "赠送点数必须是 1 至 1000000 的整数");
    }
    return (int) value;
  }

  private String normalizeReason(String rawReason) {
    String reason = rawReason == null ? "" : rawReason.trim();
    int length = reason.codePointCount(0, reason.length());
    if (length < 2 || length > 100) {
      throw new ApiException(400, "赠送原因需要 2 至 100 个字符");
    }
    return reason;
  }

  private String normalizeIdempotencyKey(String rawKey) {
    String key = rawKey == null ? "" : rawKey.trim();
    try {
      return UUID.fromString(key).toString();
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "幂等键格式不正确");
    }
  }

  private String maskEmail(String email) {
    int at = email.lastIndexOf('@');
    if (at <= 0) {
      return "***";
    }
    String local = email.substring(0, at);
    String prefix = local.substring(0, Math.min(2, local.length()));
    return prefix + "***@" + email.substring(at + 1);
  }

  private int monthlyLeft(Map<String, Object> account) {
    return Math.max(intValue(account.get("monthly_quota"))
        - intValue(account.get("monthly_used")), 0);
  }

  private int intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return value == null ? 0 : Integer.parseInt(String.valueOf(value));
  }
}
