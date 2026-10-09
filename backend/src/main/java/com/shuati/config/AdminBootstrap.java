package com.shuati.config;

import com.shuati.billing.PointAccountService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 生产空库首次启动时创建管理员。账号已存在则跳过，绝不覆盖密码。
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final PointAccountService points;

  @Value("${SHUATI_BOOTSTRAP_ADMIN_EMAIL:}")
  private String email;

  @Value("${SHUATI_BOOTSTRAP_ADMIN_PASSWORD:}")
  private String password;

  @Override
  public void run(ApplicationArguments args) {
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      return;
    }
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    List<MapRow> existing = jdbc.query(
        "select id from profiles where email = ?",
        (rs, rowNum) -> new MapRow(rs.getString(1)),
        normalized);
    if (!existing.isEmpty()) {
      jdbc.update("update profiles set role = 'ADMIN' where id = ?", existing.get(0).id());
      log.info("已存在管理员账号 {}，跳过创建", normalized);
      return;
    }
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, nickname, role, status, email_verified_at)
        values (?, ?, ?, ?, 'ADMIN', 'ENABLED', now(6))
        """, id, normalized, passwordEncoder.encode(password),
        normalized.substring(0, normalized.indexOf('@')));
    points.ensureAccount(id);
    log.info("已创建管理员账号 {}", normalized);
  }

  private record MapRow(String id) {
  }
}
