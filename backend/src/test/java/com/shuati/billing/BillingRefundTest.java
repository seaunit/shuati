package com.shuati.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款扣点：退款成功要扣回点数，且必须幂等、不能扣成负数、不能被支付事件重新发货。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BillingRefundTest {

  @Autowired BillingService billingService;
  @Autowired PointAccountService points;
  @Autowired JdbcTemplate jdbc;

  @Test
  void refundClawsBackPurchasedPointsAndIsIdempotent() {
    String userId = createUser();
    points.ensureAccount(userId);
    jdbc.update("update point_account set bonus_balance = 0 where user_id = ?", userId);
    String orderId = createOrder(userId, "pack_9", 900, 400);

    billingService.settleOrder(orderId, "PAID", true);
    assertThat(bonusOf(userId)).isEqualTo(400);

    Map<String, Object> first = billingService.refundOrder(orderId, 900, "用户申请退款");
    assertThat(first.get("clawedBack")).isEqualTo(400);
    assertThat(bonusOf(userId)).isZero();
    assertThat(statusOf(orderId)).isEqualTo("REFUNDED");
    assertThat(jdbc.queryForObject(
        "select refund_clawed_points from subscription_order where id = ?", Integer.class, orderId))
        .isEqualTo(400);
    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'REFUND_CLAWBACK' and ref_id = ?
        """, Integer.class, userId, orderId)).isEqualTo(1);

    // 重复收到退款事件：不能扣第二次
    Map<String, Object> second = billingService.refundOrder(orderId, 900, "用户申请退款");
    assertThat(second.get("alreadyRefunded")).isEqualTo(true);
    assertThat(bonusOf(userId)).isZero();
    assertThat(jdbc.queryForObject("""
        select count(*) from point_ledger
         where user_id = ? and reason = 'REFUND_CLAWBACK' and ref_id = ?
        """, Integer.class, userId, orderId)).isEqualTo(1);

    // 退款后又来一次 order.completed：不能把点数重新发一遍
    billingService.settleOrder(orderId, "PAID", true);
    assertThat(bonusOf(userId)).isZero();
    assertThat(statusOf(orderId)).isEqualTo("REFUNDED");
  }

  @Test
  void refundOnlyClawsBackWhatIsLeftWhenPointsWereSpent() {
    String userId = createUser();
    points.ensureAccount(userId);
    jdbc.update("update point_account set bonus_balance = 0 where user_id = ?", userId);
    String orderId = createOrder(userId, "pack_29", 2900, 1400);
    billingService.settleOrder(orderId, "PAID", true);

    // 模拟用户已经花掉 1200 点，只剩 200
    jdbc.update("update point_account set bonus_balance = 200 where user_id = ?", userId);

    Map<String, Object> result = billingService.refundOrder(orderId, 2900, "误购");

    assertThat(result.get("clawedBack")).isEqualTo(200);
    assertThat(result.get("expected")).isEqualTo(1400);
    assertThat(bonusOf(userId)).isZero();
    assertThat(statusOf(orderId)).isEqualTo("REFUNDED");
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "refund-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private String createOrder(String userId, String packCode, int amountCents, int pointsValue) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into subscription_order
          (id, user_id, kind, item_code, amount_cents, points, status)
        values (?, ?, 'PACK', ?, ?, ?, 'PENDING')
        """, id, userId, packCode, amountCents, pointsValue);
    return id;
  }

  private int bonusOf(String userId) {
    Integer value = jdbc.queryForObject(
        "select bonus_balance from point_account where user_id = ?", Integer.class, userId);
    return value == null ? 0 : value;
  }

  private String statusOf(String orderId) {
    return jdbc.queryForObject(
        "select status from subscription_order where id = ?", String.class, orderId);
  }
}
