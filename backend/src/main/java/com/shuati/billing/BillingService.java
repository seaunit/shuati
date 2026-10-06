package com.shuati.billing;

import com.shuati.common.ApiException;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingService {

  private final JdbcTemplate jdbc;
  private final Json json;
  private final PointAccountService points;

  public Map<String, Object> catalog() {
    List<Map<String, Object>> plans = json.normalize(jdbc.queryForList("""
        select * from plan
         where status = 'ENABLED' and show_on_pricing = 1
         order by sort asc
        """), Set.of("features"));
    List<Map<String, Object>> packs = jdbc.queryForList("""
        select code, name, price_cents, points, bonus_points, sort
          from point_pack where status = 'ENABLED' order by sort asc
        """);
    List<Map<String, Object>> rules = points.priceRules();
    int signupBonus = jdbc.queryForList(
        "select value from billing_config where `key` = 'signup_bonus_points'")
        .stream().findFirst()
        .map(row -> {
          try {
            return Integer.parseInt(String.valueOf(row.get("value")).replace("\"", ""));
          } catch (NumberFormatException e) {
            return 100;
          }
        }).orElse(100);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("plans", plans);
    result.put("packs", packs);
    result.put("rules", rules);
    result.put("signupBonus", signupBonus);
    return result;
  }

  public List<Map<String, Object>> orders(String userId) {
    return json.normalize(jdbc.queryForList("""
        select * from subscription_order
         where user_id = ? order by created_at desc limit 50
        """, userId), Set.of());
  }

  @Transactional
  public Map<String, Object> createOrder(String userId, String kind, String itemCode, String period) {
    String normalizedKind = kind == null ? "" : kind.toUpperCase();
    if (!List.of("PLAN", "PACK").contains(normalizedKind)) {
      throw new ApiException(400, "订单类型不正确");
    }
    if (itemCode == null || itemCode.isBlank()) {
      throw new ApiException(400, "缺少商品编码");
    }

    int amountCents;
    int orderPoints = 0;
    String normalizedPeriod = null;

    if ("PACK".equals(normalizedKind)) {
      List<Map<String, Object>> packs = jdbc.queryForList("""
          select code, price_cents, points, bonus_points, status from point_pack where code = ?
          """, itemCode);
      if (packs.isEmpty() || !"ENABLED".equals(packs.get(0).get("status"))) {
        throw new ApiException(404, "加量包不存在或已下架");
      }
      Map<String, Object> pack = packs.get(0);
      amountCents = intValue(pack.get("price_cents"));
      orderPoints = intValue(pack.get("points")) + intValue(pack.get("bonus_points"));
    } else {
      normalizedPeriod = period == null ? "" : period.toUpperCase();
      if (!List.of("MONTHLY", "QUARTERLY", "YEARLY").contains(normalizedPeriod)) {
        throw new ApiException(400, "请选择订阅周期");
      }
      List<Map<String, Object>> plans = jdbc.queryForList("""
          select code, status, price_monthly_cents, price_quarterly_cents, price_yearly_cents
            from plan where code = ?
          """, itemCode);
      if (plans.isEmpty() || !"ENABLED".equals(plans.get(0).get("status"))) {
        throw new ApiException(404, "套餐不存在或已下架");
      }
      Map<String, Object> plan = plans.get(0);
      amountCents = switch (normalizedPeriod) {
        case "MONTHLY" -> intValue(plan.get("price_monthly_cents"));
        case "QUARTERLY" -> intValue(plan.get("price_quarterly_cents"));
        default -> intValue(plan.get("price_yearly_cents"));
      };
      if (amountCents <= 0) {
        throw new ApiException(400, "该套餐暂不支持在线购买，请联系管理员");
      }
    }

    String orderId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into subscription_order
          (id, user_id, kind, item_code, period, amount_cents, points, status)
        values (?, ?, ?, ?, ?, ?, ?, 'PENDING')
        """, orderId, userId, normalizedKind, itemCode, normalizedPeriod, amountCents, orderPoints);

    return json.normalize(jdbc.queryForMap("""
        select id, status, amount_cents, points, kind, item_code, period, created_at
          from subscription_order where id = ?
        """, orderId), Set.of());
  }

  @Transactional
  public void settleOrder(String orderId, String action) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select * from subscription_order where id = ? for update", orderId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "订单不存在");
    }
    Map<String, Object> order = rows.get(0);
    if ("PAID".equals(order.get("status"))) {
      throw new ApiException(400, "订单已结算，请勿重复操作");
    }

    String normalizedAction = action == null ? "PAID" : action.toUpperCase();
    if ("CANCELLED".equals(normalizedAction)) {
      jdbc.update("update subscription_order set status = 'CANCELLED' where id = ?", orderId);
      return;
    }
    if (!"PAID".equals(normalizedAction)) {
      throw new ApiException(400, "不支持的结算操作");
    }

    String userId = String.valueOf(order.get("user_id"));
    if ("PACK".equals(order.get("kind"))) {
      points.addBonusPoints(userId, intValue(order.get("points")), "PACK_PURCHASE", "order", orderId);
    } else {
      LocalDateTime expiresAt = expiryFor(String.valueOf(order.get("period")));
      points.applyPlan(userId, String.valueOf(order.get("item_code")), expiresAt);
    }
    jdbc.update("""
        update subscription_order
           set status = 'PAID', paid_at = now(6), provider = coalesce(provider, 'manual')
         where id = ?
        """, orderId);
  }

  public List<Map<String, Object>> allOrders(int limit) {
    return json.normalize(jdbc.queryForList("""
        select id, user_id, kind, item_code, period, amount_cents, points, status, created_at, paid_at
          from subscription_order order by created_at desc limit ?
        """, Math.max(1, Math.min(limit, 200))), Set.of());
  }

  public Map<String, Object> adminSummary() {
    List<Map<String, Object>> accounts = jdbc.queryForList(
        "select monthly_quota, monthly_used, bonus_balance, lifetime_used from point_account");
    long outstanding = 0;
    long consumed = 0;
    for (Map<String, Object> account : accounts) {
      outstanding += Math.max(intValue(account.get("monthly_quota"))
          - intValue(account.get("monthly_used")), 0) + intValue(account.get("bonus_balance"));
      consumed += intValue(account.get("lifetime_used"));
    }
    List<Map<String, Object>> orders = jdbc.queryForList(
        "select status, amount_cents from subscription_order");
    long revenue = orders.stream().filter(o -> "PAID".equals(o.get("status")))
        .mapToLong(o -> intValue(o.get("amount_cents"))).sum();
    long pending = orders.stream().filter(o -> "PENDING".equals(o.get("status"))).count();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("userCount", accounts.size());
    stats.put("outstandingPoints", outstanding);
    stats.put("consumedPoints", consumed);
    stats.put("paidRevenueCents", revenue);
    stats.put("pendingOrders", pending);
    return stats;
  }

  private LocalDateTime expiryFor(String period) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    return switch (period) {
      case "YEARLY" -> now.plusYears(1);
      case "QUARTERLY" -> now.plusMonths(3);
      default -> now.plusMonths(1);
    };
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
}

