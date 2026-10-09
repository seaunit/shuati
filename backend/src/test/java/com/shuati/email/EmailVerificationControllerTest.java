package com.shuati.email;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationControllerTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  EmailSender sender;

  @Test
  void sendCodeReturnsCooldownWithoutCaptcha() throws Exception {
    String email = "send-" + UUID.randomUUID() + "@example.com";

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
        .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
  }

  @Test
  void registeredEmailIsRejected() throws Exception {
    String email = "registered-" + UUID.randomUUID() + "@example.com";
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, UUID.randomUUID().toString(), email);

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void repeatedSendReturnsRetryAfter() throws Exception {
    String email = "cooldown-" + UUID.randomUUID() + "@example.com";

    send(email);

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.message", containsString("秒后重试")));
  }

  private void send(String email) throws Exception {
    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)))
        .andExpect(status().isOk());
  }
}
