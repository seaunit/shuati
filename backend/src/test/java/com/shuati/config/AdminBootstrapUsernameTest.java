package com.shuati.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest
class AdminBootstrapUsernameTest {

  private static final String ADMIN_USERNAME = "admin-" + UUID.randomUUID();
  private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();

  @Autowired
  JdbcTemplate jdbc;

  @DynamicPropertySource
  static void bootstrapProperties(DynamicPropertyRegistry registry) {
    registry.add("SHUATI_BOOTSTRAP_ADMIN_EMAIL", () -> ADMIN_USERNAME);
    registry.add("SHUATI_BOOTSTRAP_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
  }

  @Test
  void bootstrapAdminSupportsANonEmailAccountName() {
    Map<String, Object> profile = jdbc.queryForMap("""
        select email, nickname, role, status
          from profiles
         where email = ?
        """, ADMIN_USERNAME);

    assertThat(profile.get("email")).isEqualTo(ADMIN_USERNAME);
    assertThat(profile.get("nickname")).isEqualTo(ADMIN_USERNAME);
    assertThat(profile.get("role")).isEqualTo("ADMIN");
    assertThat(profile.get("status")).isEqualTo("ENABLED");
  }
}
