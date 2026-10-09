package com.shuati.config;

import static org.assertj.core.api.Assertions.assertThat;

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
class AdminBootstrapEmailVerificationTest {

  private static final String ADMIN_EMAIL =
      "bootstrap-" + UUID.randomUUID() + "@example.com";

  @Autowired
  JdbcTemplate jdbc;

  @DynamicPropertySource
  static void bootstrapProperties(DynamicPropertyRegistry registry) {
    registry.add("SHUATI_BOOTSTRAP_ADMIN_EMAIL", () -> ADMIN_EMAIL);
    registry.add("SHUATI_BOOTSTRAP_ADMIN_PASSWORD", () -> "Bootstrap@2026");
  }

  @Test
  void bootstrapAdminIsMarkedEmailVerified() {
    String verifiedAt = jdbc.queryForObject(
        "select email_verified_at from profiles where email = ?",
        String.class,
        ADMIN_EMAIL);

    assertThat(verifiedAt).isNotBlank();
  }
}
