package com.shuati.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class RateLimiterTest {

  @Autowired
  RateLimiter rateLimiter;

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
  void allowsUpToLimitThenRejects() {
    String key = "unit:" + UUID.randomUUID();
    Duration window = Duration.ofMinutes(1);

    assertThat(rateLimiter.allow(key, 3, window)).isTrue();
    assertThat(rateLimiter.allow(key, 3, window)).isTrue();
    assertThat(rateLimiter.allow(key, 3, window)).isTrue();
    assertThat(rateLimiter.allow(key, 3, window)).isFalse();
    assertThat(rateLimiter.current(key)).isEqualTo(4L);
  }

  @Test
  void zeroLimitMeansUnlimited() {
    assertThat(rateLimiter.allow("disabled:" + UUID.randomUUID(), 0, Duration.ofMinutes(1)))
        .isTrue();
  }
}
