package com.shuati.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuati.auth.JwtAuthFilter;
import com.shuati.auth.JwtService;
import com.shuati.email.EmailPurpose;
import com.shuati.email.EmailDispatchService;
import com.shuati.email.EmailVerificationProperties;
import com.shuati.email.EmailVerificationService;
import com.shuati.email.EmailVerificationStore;
import com.shuati.email.EmailVerificationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetFlowTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired EmailVerificationStore emailStore;
  @Autowired EmailVerificationService emailService;
  @Autowired JwtService jwtService;
  @MockitoBean EmailDispatchService dispatch;

  @BeforeEach
  void resetSender() {
    org.mockito.Mockito.reset(dispatch);
  }

  @Test
  void existingAndMissingEmailReturnSameSendResult() throws Exception {
    String existing = "reset-" + UUID.randomUUID() + "@example.com";
    insertUser(existing, "old-password");
    String missing = "missing-" + UUID.randomUUID() + "@example.com";

    expectCodeSent(existing, true);
    expectCodeSent(missing, false);
  }

  @Test
  void resetChangesPasswordAndRejectsOldJwt() throws Exception {
    String email = "reset-jwt-" + UUID.randomUUID() + "@example.com";
    String userId = insertUser(email, "old-password");
    String oldJwt = jwtService.issue(userId, "USER", 0);
    seedResetCode(email, "123456");

    reset(email, "123456", "new-password").andExpect(status().isOk());

    mvc.perform(get("/api/me")
            .cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, oldJwt)))
        .andExpect(status().isUnauthorized());

    Map<String, Object> profile = jdbc.queryForMap(
        "select password_hash, session_version from profiles where id = ?", userId);
    assertThat(passwordEncoder.matches(
        "new-password", String.valueOf(profile.get("password_hash")))).isTrue();
    assertThat(passwordEncoder.matches(
        "old-password", String.valueOf(profile.get("password_hash")))).isFalse();
    assertThat(((Number) profile.get("session_version")).intValue()).isEqualTo(1);
  }

  @Test
  void resetCodeCannotBeReused() throws Exception {
    String email = "reset-reuse-" + UUID.randomUUID() + "@example.com";
    insertUser(email, "old-password");
    seedResetCode(email, "123456");

    reset(email, "123456", "first-new-password").andExpect(status().isOk());
    reset(email, "123456", "second-new-password")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
  }

  private void expectCodeSent(String email, boolean senderCalled) throws Exception {
    clearInvocations(dispatch);
    mvc.perform(post("/api/auth/password-reset/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
        .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    if (senderCalled) {
      ArgumentCaptor<EmailVerificationService.PreparedCode> captor =
          ArgumentCaptor.forClass(EmailVerificationService.PreparedCode.class);
      verify(dispatch).send(captor.capture());
      assertThat(captor.getValue().email()).isEqualTo(email);
      assertThat(captor.getValue().purpose()).isEqualTo(EmailPurpose.RESET);
    } else {
      verify(dispatch, never()).send(any(EmailVerificationService.PreparedCode.class));
    }
  }

  private ResultActions reset(String email, String code, String password) throws Exception {
    return mvc.perform(post("/api/auth/password-reset")
        .header("X-Requested-With", "ShuatiApp")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email":"%s","emailCode":"%s","newPassword":"%s"}
            """.formatted(email, code, password)));
  }

  private String insertUser(String email, String password) {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, ?, 'USER', 'ENABLED', 0)
        """, userId, email, passwordEncoder.encode(password));
    return userId;
  }

  private void seedResetCode(String email, String code) {
    EmailVerificationTestSupport.seed(
        emailStore,
        emailService,
        new EmailVerificationProperties(
            true, "no-reply@example.com", "拾题", "test-email-secret",
            300, 60, 5, 10, 20, 30, 2000),
        email,
        code,
        EmailPurpose.RESET);
  }
}
