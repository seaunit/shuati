package com.shuati.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class JwtSessionVersionTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  JwtService jwtService;

  @Test
  void tokenWithStaleSessionVersionIsRejected() throws Exception {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, 'x', 'USER', 'ENABLED', 1)
        """, userId, "session-" + userId + "@example.com");

    String stale = jwtService.issue(userId, "USER", 0);
    mvc.perform(get("/api/me")
            .cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, stale)))
        .andExpect(status().isUnauthorized());
  }
}
