package com.shuati.captcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 操作封装。key 统一前缀 {@code captcha:}，不使用 HttpSession，天然适配分布式集群。
 */
@Component
@RequiredArgsConstructor
public class CaptchaStore {

  private static final String KEY_PREFIX = "captcha:";
  /**
   * 校验脚本（Lua 保证原子性，避免并发重复校验）：
   * 命中立即删除（一次性）；错误累加次数；达到上限直接作废。
   */
  private static final DefaultRedisScript<List> VERIFY_SCRIPT = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then
        return {'EXPIRED', 0}
      end
      local entry = cjson.decode(value)
      entry.attempts = tonumber(entry.attempts) + 1
      if entry.code == ARGV[1] then
        redis.call('DEL', KEYS[1])
        return {'OK', entry.attempts}
      end
      if entry.attempts >= tonumber(ARGV[2]) then
        redis.call('DEL', KEYS[1])
        return {'LOCKED', entry.attempts}
      end
      local ttl = redis.call('TTL', KEYS[1])
      if ttl < 1 then ttl = 1 end
      redis.call('SET', KEYS[1], cjson.encode(entry), 'EX', ttl)
      return {'MISMATCH', entry.attempts}
      """, List.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public void save(String ticket, String storedCode, Duration ttl) {
    try {
      String json = objectMapper.writeValueAsString(new CaptchaEntry(storedCode, 0));
      redis.opsForValue().set(KEY_PREFIX + ticket, json, ttl);
    } catch (Exception e) {
      throw new IllegalStateException("验证码写入 Redis 失败", e);
    }
  }

  /** @return [status, attempts]，status ∈ OK / MISMATCH / LOCKED / EXPIRED */
  public List<?> verify(String ticket, String submittedCode, int maxAttempts) {
    return redis.execute(VERIFY_SCRIPT, List.of(KEY_PREFIX + ticket),
        submittedCode, String.valueOf(maxAttempts));
  }

  public String rawEntry(String ticket) {
    return redis.opsForValue().get(KEY_PREFIX + ticket);
  }

  public void delete(String ticket) {
    redis.delete(KEY_PREFIX + ticket);
  }

  public record CaptchaEntry(String code, int attempts) {
  }
}
