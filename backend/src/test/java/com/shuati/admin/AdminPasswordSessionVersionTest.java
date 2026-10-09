package com.shuati.admin;

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
class AdminPasswordSessionVersionTest {

  @Autowired
  AdminUserController controller;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void adminPasswordChangeIncrementsSessionVersion() {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, 'x', 'USER', 'ENABLED', 0)
        """, userId, "admin-change-" + userId + "@example.com");

    controller.updateUser(userId, Map.of("password", "new-password"));

    Integer version = jdbc.queryForObject(
        "select session_version from profiles where id = ?",
        Integer.class,
        userId);
    assertThat(version).isEqualTo(1);
  }
}
