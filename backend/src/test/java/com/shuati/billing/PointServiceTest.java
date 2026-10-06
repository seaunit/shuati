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
