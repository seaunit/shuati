package com.shuati.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.auth.dto.LoginRequest;
import com.shuati.captcha.CaptchaImageResponse;
import com.shuati.captcha.CaptchaService;
import com.shuati.captcha.CaptchaStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class LoginResetRaceTest {

  @Autowired AuthService authService;
  @Autowired JdbcTemplate jdbc;
  @Autowired CaptchaService captchaService;
  @Autowired CaptchaStore captchaStore;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean PasswordEncoder passwordEncoder;

  @Test
  void loginCannotOverwritePasswordResetCommittedAfterCredentialCheck() throws Exception {
    String email = "race-" + UUID.randomUUID() + "@example.com";
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, 'old-hash', 'USER', 'ENABLED', 0)
        """, userId, email);

    CaptchaImageResponse captcha = captchaService.generate("7.7.7.7", "race-device");
    String captchaCode = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();

    CountDownLatch checked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(invocation -> {
      checked.countDown();
      release.await(5, TimeUnit.SECONDS);
      return true;
    });

    var executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<AuthService.LoginResult> login = CompletableFuture.supplyAsync(
          () -> authService.login(new LoginRequest(
              email, "old-password", captcha.ticket(), captchaCode)),
          executor);

      assertThat(checked.await(5, TimeUnit.SECONDS)).isTrue();
      jdbc.update("""
          update profiles
             set password_hash = 'new-hash', session_version = 1
           where id = ?
          """, userId);
      release.countDown();
      login.get(5, TimeUnit.SECONDS);

      Map<String, Object> profile = jdbc.queryForMap(
          "select password_hash, session_version from profiles where id = ?", userId);
      assertThat(profile.get("password_hash")).isEqualTo("new-hash");
      assertThat(((Number) profile.get("session_version")).intValue()).isEqualTo(1);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
