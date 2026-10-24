package com.shuati.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.auth.CustomHeaderFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPointGrantApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void adminCanLookupByEmailAndGrantPointsWithAuditTrail() throws Exception {
    String adminId = createUser("ADMIN", "admin@example.com");
    String targetId = createUser("USER", "User@Example.com");
    createAccount(targetId, 20);

    mvc.perform(get("/api/admin/users/by-email")
            .param("email", "  user@example.COM ")
            .with(asAdmin(adminId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.emailMasked").value("us***@example.com"))
        .andExpect(jsonPath("$.data.entitlements.bonusBalance").value(20));

    String key = UUID.randomUUID().toString();
    mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
            .with(asAdmin(adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("points", 100, "reason", "客服补偿", "idempotencyKey", key))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.points").value(100))
        .andExpect(jsonPath("$.data.balanceAfter").value(120))
        .andExpect(jsonPath("$.data.reused").value(false));

    assertThat(bonusBalance(targetId)).isEqualTo(120);
    Map<String, Object> grant = jdbc.queryForMap(
        "select * from admin_point_grant where user_id = ?", targetId);
    assertThat(grant.get("admin_id")).isEqualTo(adminId);
    assertThat(grant.get("admin_email")).isEqualTo("admin@example.com");
    assertThat(grant.get("points")).isEqualTo(100);
    assertThat(grant.get("reason")).isEqualTo("客服补偿");
    Map<String, Object> ledger = jdbc.queryForMap(
        "select * from point_ledger where id = ?", grant.get("ledger_id"));
    assertThat(ledger.get("reason")).isEqualTo("ADMIN_GRANT");
    assertThat(ledger.get("note")).isEqualTo("客服补偿");
    assertThat(ledger.get("delta")).isEqualTo(100);
    assertThat(ledger.get("balance_after")).isEqualTo(120);
  }

  @Test
  void repeatedIdempotencyKeyOnlyGrantsOnceAndRejectsDifferentPayload() throws Exception {
    String adminId = createUser("ADMIN", "admin-repeat@example.com");
    String targetId = createUser("USER", "repeat@example.com");
    createAccount(targetId, 0);
    String key = UUID.randomUUID().toString();
    String body = json(Map.of("points", 50, "reason", "活动奖励", "idempotencyKey", key));

    for (int i = 0; i < 10; i++) {
      mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
              .with(asAdmin(adminId))
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.balanceAfter").value(50))
          .andExpect(jsonPath("$.data.reused").value(i > 0));
    }

    mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
            .with(asAdmin(adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("points", 51, "reason", "活动奖励", "idempotencyKey", key))))
        .andExpect(status().isConflict());

    assertThat(bonusBalance(targetId)).isEqualTo(50);
    assertThat(jdbc.queryForObject(
        "select count(*) from admin_point_grant where user_id = ?", Integer.class, targetId))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "select count(*) from point_ledger where user_id = ? and reason = 'ADMIN_GRANT'",
        Integer.class, targetId)).isEqualTo(1);
  }

  @Test
  void onlyAdminCanLookupAndGrant() throws Exception {
    String adminId = createUser("ADMIN", "admin-secure@example.com");
    String userId = createUser("USER", "normal@example.com");
    String targetId = createUser("USER", "target@example.com");
    createAccount(targetId, 10);
    String body = json(Map.of(
        "points", 10,
        "reason", "越权测试",
        "idempotencyKey", UUID.randomUUID().toString()));

    mvc.perform(get("/api/admin/users/by-email").param("email", "target@example.com"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
            .with(appHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/users/by-email")
            .param("email", "target@example.com")
            .with(asUser(userId)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
            .with(asUser(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden());

    assertThat(bonusBalance(targetId)).isEqualTo(10);
  }

  @Test
  void invalidPointsReasonsTargetsAndOldGrantEntryAreRejected() throws Exception {
    String adminId = createUser("ADMIN", "admin-invalid@example.com");
    String targetId = createUser("USER", "invalid-target@example.com");
    createAccount(targetId, 5);

    for (Object points : List.of(0, -1, 1.5, 1_000_001)) {
      mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
              .with(asAdmin(adminId))
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(Map.of(
                  "points", points,
                  "reason", "合法原因",
                  "idempotencyKey", UUID.randomUUID().toString()))))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post("/api/admin/users/{id}/point-grants", targetId)
            .with(asAdmin(adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "points", 10,
                "reason", "x",
                "idempotencyKey", UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/admin/users/{id}/point-grants", "missing-user")
            .with(asAdmin(adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "points", 10,
                "reason", "合法原因",
                "idempotencyKey", UUID.randomUUID().toString()))))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/admin/users/{id}/points", targetId)
            .with(asAdmin(adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("action", "GRANT", "points", 99))))
        .andExpect(status().isBadRequest());

    assertThat(bonusBalance(targetId)).isEqualTo(5);
    assertThat(jdbc.queryForObject(
        "select count(*) from admin_point_grant", Integer.class)).isZero();
  }

  private String createUser(String role, String email) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, nickname, role, status)
        values (?, ?, 'x', ?, ?, 'ENABLED')
        """, id, email, role.toLowerCase() + "-nick", role);
    return id;
  }

  private void createAccount(String userId, int bonusBalance) {
    jdbc.update("""
        insert into point_account
          (user_id, plan_code, monthly_quota, monthly_used, bonus_balance,
           period_start, period_end, lifetime_granted, lifetime_used)
        values (?, 'free', 0, 0, ?, now(6), date_add(now(6), interval 1 month), ?, 0)
        """, userId, bonusBalance, bonusBalance);
  }

  private int bonusBalance(String userId) {
    return jdbc.queryForObject(
        "select bonus_balance from point_account where user_id = ?", Integer.class, userId);
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private RequestPostProcessor asAdmin(String adminId) {
    return withAppHeader(authentication(new UsernamePasswordAuthenticationToken(
        adminId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
  }

  private RequestPostProcessor asUser(String userId) {
    return withAppHeader(authentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of())));
  }

  private RequestPostProcessor appHeader() {
    return request -> {
      request.addHeader(CustomHeaderFilter.HEADER, CustomHeaderFilter.VALUE);
      return request;
    };
  }

  private RequestPostProcessor withAppHeader(RequestPostProcessor authenticationProcessor) {
    return request -> {
      request = authenticationProcessor.postProcessRequest(request);
      request.addHeader(CustomHeaderFilter.HEADER, CustomHeaderFilter.VALUE);
      return request;
    };
  }
}
