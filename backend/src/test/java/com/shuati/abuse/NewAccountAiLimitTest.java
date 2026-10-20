package com.shuati.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuati.common.ApiException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = "shuati.abuse.new-account-ai-daily-limit=2")
class NewAccountAiLimitTest {

  @Autowired
  NewAccountAiLimit limit;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void blocksNewAccountAfterDailyLimitAndIgnoresOldAccounts() {
    String freshId = insertProfile(LocalDateTime.now(ZoneOffset.UTC));
    limit.check(freshId);
    limit.check(freshId);
    assertThatThrownBy(() -> limit.check(freshId))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> {
          ApiException ex = (ApiException) e;
          assertThat(ex.getStatus()).isEqualTo(429);
          assertThat(ex.getMessage()).contains("新账号首日 AI 次数已达上限");
        });

    // 注册超过 24 小时的账号不受这条限制
    String oldId = insertProfile(LocalDateTime.now(ZoneOffset.UTC).minusDays(3));
    limit.check(oldId);
    limit.check(oldId);
    limit.check(oldId);
  }

  private String insertProfile(LocalDateTime createdAt) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, role, status, created_at)
        values (?, ?, 'USER', 'ENABLED', ?)
        """, id, "t-" + id + "@example.com", createdAt);
    return id;
  }
}
