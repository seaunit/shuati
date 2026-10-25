package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class PracticeLastScopeTest {

  @Autowired PracticeService service;
  @Autowired PracticeController controller;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void latestBankOrUnitWinsEvenWhenANewerAllSessionExists() {
    String userId = createUser();
    long bankId = createBank(null);
    long unitId = createUnit(bankId);
    insertSession(userId, "BANK", bankId, "JAVA八股", "2026-10-25 08:00:00");
    insertSession(userId, "UNIT", unitId, "第一单元", "2026-10-25 09:00:00");
    insertSession(userId, "ALL", null, "全部题目", "2026-10-25 10:00:00");
    authenticate(userId, false);

    Map<String, Object> scope = service.latestScopedSession();

    assertThat(scope.get("scope_type")).isEqualTo("UNIT");
    assertThat(((Number) scope.get("scope_id")).longValue()).isEqualTo(unitId);
  }

  @Test
  void onlyOwnerAndAdminCanRecoverTheirSessionsForPrivateBanks() {
    String ownerId = createUser();
    String otherId = createUser();
    String adminId = createUser();
    long publicBankId = createBank(null);
    long privateBankId = createBank(ownerId);
    insertSession(ownerId, "BANK", privateBankId, "私有题库", "2026-10-25 10:00:00");
    insertSession(otherId, "BANK", publicBankId, "公共题库", "2026-10-25 10:00:00");
    insertSession(adminId, "BANK", privateBankId, "管理员恢复私有题库", "2026-10-25 10:00:00");

    authenticate(ownerId, false);
    assertThat(((Number) service.latestScopedSession().get("scope_id")).longValue())
        .isEqualTo(privateBankId);

    authenticate(otherId, false);
    assertThat(((Number) service.latestScopedSession().get("scope_id")).longValue())
        .isEqualTo(publicBankId);

    authenticate(adminId, true);
    assertThat(((Number) service.latestScopedSession().get("scope_id")).longValue())
        .isEqualTo(privateBankId);
  }

  @Test
  void deletedScopesAreSkippedAndHttpEndpointUsesTheSameQuery() {
    String userId = createUser();
    long validBankId = createBank(null);
    long deletedBankId = createBank(null);
    insertSession(userId, "BANK", validBankId, "仍存在", "2026-10-25 08:00:00");
    insertSession(userId, "BANK", deletedBankId, "已删除", "2026-10-25 09:00:00");
    jdbc.update("delete from bank where id = ?", deletedBankId);
    authenticate(userId, false);

    assertThat(((Number) service.latestScopedSession().get("scope_id")).longValue())
        .isEqualTo(validBankId);
    assertThat(((Number) controller.lastScope().data().get("scope_id")).longValue())
        .isEqualTo(validBankId);
  }

  @Test
  void noValidBankOrUnitReturnsAnEmptyScope() {
    String userId = createUser();
    String otherId = createUser();
    long otherPrivateBankId = createBank(otherId);
    insertSession(userId, "BANK", otherPrivateBankId, "别人的题库", "2026-10-25 10:00:00");
    insertSession(userId, "ALL", null, "全部题目", "2026-10-25 11:00:00");
    authenticate(userId, false);

    assertThat(service.latestScopedSession()).isEmpty();
    assertThat(controller.lastScope().data()).isEmpty();
  }

  private void authenticate(String userId, boolean admin) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()));
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "last-scope-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createBank(String ownerId) {
    String name = "最近范围测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, name);
  }

  private long createUnit(long bankId) {
    String name = "最近范围单元-" + UUID.randomUUID();
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 1)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private void insertSession(
      String userId, String scopeType, Long scopeId, String scopeName, String startedAt) {
    jdbc.update("""
        insert into practice_session
          (user_id, scope_type, scope_id, scope_name, total_questions, started_at)
        values (?, ?, ?, ?, 100, ?)
        """, userId, scopeType, scopeId, scopeName, startedAt);
  }
}
