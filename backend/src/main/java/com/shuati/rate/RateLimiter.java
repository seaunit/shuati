package com.shuati.rate;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的固定窗口计数器。
 *
 * <p>用 INCR + EXPIRE 实现：第一次自增时设置过期时间，窗口结束自动清零。
 * 只依赖 Redis，天然支持多实例集群（各节点共享同一份计数）。
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
  private static final String PREFIX = "rate:";

  private final StringRedisTemplate redis;

  /** @return true=放行，false=超出限制 */
  public boolean allow(String key, int limit, Duration window) {
    if (limit <= 0) {
      return true;
    }
    try {
      String redisKey = PREFIX + key;
      Long count = redis.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redis.expire(redisKey, window);
      }
      return count != null && count <= limit;
    } catch (Exception e) {
      // Redis 不可用时 fail-open：限流只是兜底防线，不应让整站 500。
      // 真正的安全边界（验证码校验）本身就依赖 Redis，会独立失败。
      log.warn("限流依赖的 Redis 不可用，本次放行：{}", e.getMessage());
      return true;
    }
  }

  public long current(String key) {
    String value = redis.opsForValue().get(PREFIX + key);
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
