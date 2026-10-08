package com.shuati.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PointServiceTest {

  @Autowired
  PointAccountService points;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void consumePointsIsNotResetByPeriodRenewal() {
    String userId = createUser();
    points.ensureAccount(userId);

    int before = intValue(points.entitlements(userId).get("available"));
    assertThat(before).isEqualTo(150);

    Map<String, Object> charged = points.consumePoints(userId, "JUDGE", null, "test", "1");
    assertThat(charged.get("ok")).isEqualTo(true);
    assertThat(intValue(charged.get("cost"))).isEqualTo(3);

    int after = intValue(points.entitlements(userId).get("available"));
    assertThat(after).isEqualTo(before - 3);

    Map<String, Object> account = jdbc.queryForMap(
        "select monthly_used, bonus_balance, lifetime_used from point_account where user_id = ?",
        userId);
    assertThat(intValue(account.get("lifetime_used"))).isEqualTo(3);
    assertThat(intValue(account.get("monthly_used"))).isEqualTo(3);
  }

  @Test
  void refundReturnsPoints() {
    String userId = createUser();
    points.ensureAccount(userId);
    points.consumePoints(userId, "JUDGE", null, "test", "2");
    points.refundPoints(userId, 3, "AI_JUDGE_FAILED", "test", "2");

    Map<String, Object> account = jdbc.queryForMap(
        "select monthly_used, lifetime_used from point_account where user_id = ?", userId);
    assertThat(intValue(account.get("monthly_used"))).isEqualTo(0);
    assertThat(intValue(account.get("lifetime_used"))).isEqualTo(0);
  }

  @Test
  void freeMonthlyGrantIsOnlyATopUpToFifty() {
    String userId = createUser();
    points.ensureAccount(userId); // 50 月度 + 100 注册赠 = 150

    // 场景一：资产 150 >= 50，到期后不再赠送
    jdbc.update(
        "update point_account set period_end = date_sub(now(6), interval 1 day) where user_id = ?",
        userId);
    points.renewPeriod(userId);
    Map<String, Object> afterRich = jdbc.queryForMap(
        "select monthly_quota, monthly_used, bonus_balance from point_account where user_id = ?",
        userId);
    assertThat(intValue(afterRich.get("monthly_quota"))).isZero();
    assertThat(intValue(afterRich.get("bonus_balance"))).isEqualTo(100);
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(100);

    // 场景二：资产降到 10，只补差额到 50
    jdbc.update("""
        update point_account
           set bonus_balance = 10, monthly_quota = 0, monthly_used = 0,
               period_end = date_sub(now(6), interval 1 day)
         where user_id = ?
        """, userId);
    points.renewPeriod(userId);
    Map<String, Object> afterPoor = jdbc.queryForMap(
        "select monthly_quota, bonus_balance from point_account where user_id = ?", userId);
    assertThat(intValue(afterPoor.get("monthly_quota"))).isEqualTo(40);
    assertThat(intValue(afterPoor.get("bonus_balance"))).isEqualTo(10);
    assertThat(intValue(points.entitlements(userId).get("available"))).isEqualTo(50);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "point-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }
}
