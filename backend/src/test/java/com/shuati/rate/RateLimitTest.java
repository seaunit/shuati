package com.shuati.rate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 单独打开限流开关，验证接口级限流真的会拦。 */
@ActiveProfiles("test")
@SpringBootTest(properties = "shuati.rate-limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitTest {

  private static final String LOGIN_BODY =
      "{\"email\":\"rate@example.com\",\"password\":\"secret123\"}";

  @Autowired
  MockMvc mvc;

  @Autowired
  StringRedisTemplate redis;

  @BeforeEach
  void cleanRedis() {
    Set<String> keys = redis.keys("rate:*");
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  @Test
  void loginIsRateLimitedPerIp() throws Exception {
    // 前 10 次：验证码不通过，返回 400
    for (int i = 0; i < 10; i++) {
      mvc.perform(post("/api/auth/login")
              .header("X-Requested-With", "ShuatiApp")
              .contentType(MediaType.APPLICATION_JSON)
              .content(LOGIN_BODY))
          .andExpect(status().isBadRequest());
    }
    // 第 11 次：被限流拦下
    mvc.perform(post("/api/auth/login")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content(LOGIN_BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));
  }
}
