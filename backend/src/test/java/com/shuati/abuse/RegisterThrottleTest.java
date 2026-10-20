package com.shuati.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuati.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
    "shuati.abuse.register-ip-hourly-limit=2",
    "shuati.abuse.register-ip-daily-limit=2"
})
class RegisterThrottleTest {

  @Autowired
  RegisterThrottle throttle;

  @Test
  void blocksAfterHourlyLimit() {
    String ip = "203.0.113." + (1 + Math.abs(UUID.randomUUID().hashCode() % 200));
    throttle.checkIp(ip);
    throttle.checkIp(ip);
    assertThatThrownBy(() -> throttle.checkIp(ip))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> {
          ApiException ex = (ApiException) e;
          assertThat(ex.getStatus()).isEqualTo(429);
          assertThat(ex.getMessage()).contains("注册过于频繁");
        });
  }
}
