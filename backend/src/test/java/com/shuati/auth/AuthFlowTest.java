package com.shuati.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void registerCreatesPointAccountAndAllowsMe() throws Exception {
    String email = "t-" + UUID.randomUUID() + "@example.com";

    MvcResult registered = mvc.perform(post("/api/auth/register")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, "secret123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.status").value("ENABLED"))
        .andExpect(cookie().exists(JwtAuthFilter.COOKIE_NAME))
        .andReturn();

    Cookie token = registered.getResponse().getCookie(JwtAuthFilter.COOKIE_NAME);
    assertThat(token).isNotNull();

    mvc.perform(get("/api/me").cookie(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email));

    Map<String, Object> account = jdbc.queryForMap("""
        select pa.monthly_quota, pa.monthly_used, pa.bonus_balance, pa.lifetime_granted
          from point_account pa
          join profiles p on p.id = pa.user_id
         where p.email = ?
        """, email);
    assertThat(((Number) account.get("monthly_quota")).intValue()).isEqualTo(50);
    assertThat(((Number) account.get("bonus_balance")).intValue()).isEqualTo(100);
    assertThat(((Number) account.get("lifetime_granted")).intValue()).isEqualTo(150);
  }

  @Test
  void duplicateEmailIsRejected() throws Exception {
    String email = "t-" + UUID.randomUUID() + "@example.com";
    mvc.perform(post("/api/auth/register").header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "secret123")))
        .andExpect(status().isOk());

    mvc.perform(post("/api/auth/register").header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "secret123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("该邮箱已注册，请直接登录"));
  }

  @Test
  void wrongPasswordReturns401() throws Exception {
    String email = "t-" + UUID.randomUUID() + "@example.com";
    mvc.perform(post("/api/auth/register").header("X-Requested-With", "ShuatiApp")
        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "secret123")));

    mvc.perform(post("/api/auth/login").header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "wrong-pass")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
  }

  @Test
  void legacySupabaseBcryptHashCanLogin() throws Exception {
    String email = "legacy-" + UUID.randomUUID() + "@example.com";
    // Supabase 存的是 $2a$10$ 前缀的 bcrypt，这里用同样的版本与强度生成
    String legacyHash = new BCryptPasswordEncoder(BCryptVersion.$2A, 10).encode("password");
    jdbc.update("""
        insert into profiles (id, email, password_hash, nickname, role, status)
        values (?, ?, ?, 'legacy', 'USER', 'ENABLED')
        """, UUID.randomUUID().toString(), email, legacyHash);

    mvc.perform(post("/api/auth/login").header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "password")))
        .andExpect(status().isOk())
        .andExpect(cookie().exists(JwtAuthFilter.COOKIE_NAME));
  }

  @Test
  void meWithoutLoginReturns401() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("请先登录"));
  }

  private String credentials(String email, String password) throws Exception {
    Map<String, String> body = new HashMap<>();
    body.put("email", email);
    body.put("password", password);
    return objectMapper.writeValueAsString(body);
  }
}

