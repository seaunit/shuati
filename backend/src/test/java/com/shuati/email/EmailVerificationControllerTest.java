package com.shuati.email;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.captcha.CaptchaImageResponse;
import com.shuati.captcha.CaptchaService;
import com.shuati.captcha.CaptchaStore;
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
  CaptchaService captchaService;

  @Autowired
  CaptchaStore captchaStore;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  EmailSender sender;

  @Test
  void sendCodeRequiresCaptchaAndReturnsCooldown() throws Exception {
    String email = "send-" + UUID.randomUUID() + "@example.com";
    CaptchaImageResponse captcha = captchaService.generate("9.9.9.9", "device-x");
    String code = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","captchaTicket":"%s","captchaCode":"%s"}
                """.formatted(email, captcha.ticket(), code)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
        .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
  }

  @Test
  void registeredEmailIsRejectedAfterCaptcha() throws Exception {
    String email = "registered-" + UUID.randomUUID() + "@example.com";
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, UUID.randomUUID().toString(), email);
    CaptchaImageResponse captcha = captchaService.generate("9.9.9.10", "device-y");
    String code = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","captchaTicket":"%s","captchaCode":"%s"}
                """.formatted(email, captcha.ticket(), code)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void repeatedSendReturnsRetryAfter() throws Exception {
    String email = "cooldown-" + UUID.randomUUID() + "@example.com";

    send(email, "9.9.9.11", "device-z");
    CaptchaImageResponse captcha = captchaService.generate("9.9.9.12", "device-z2");
    String code = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();

    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","captchaTicket":"%s","captchaCode":"%s"}
                """.formatted(email, captcha.ticket(), code)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.message", containsString("秒后重试")));
  }

  private void send(String email, String ip, String device) throws Exception {
    CaptchaImageResponse captcha = captchaService.generate(ip, device);
    String code = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();
    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","captchaTicket":"%s","captchaCode":"%s"}
                """.formatted(email, captcha.ticket(), code)))
        .andExpect(status().isOk());
  }
}
