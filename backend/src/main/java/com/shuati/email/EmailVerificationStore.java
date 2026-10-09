package com.shuati.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis 邮箱验证码存储，验证码只保存哈希。 */
@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

  private static final String PREFIX = "email:register:";

  private static final DefaultRedisScript<List> VERIFY_SCRIPT = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then
        return {'EXPIRED', 0}
      end
      local entry = cjson.decode(value)
      entry.attempts = tonumber(entry.attempts) + 1
      if entry.codeHash == ARGV[1] then
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

  public boolean acquireCooldown(String emailHash, int seconds) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
        cooldownKey(emailHash), "1", Duration.ofSeconds(seconds)));
  }

  public void saveCode(String emailHash, String codeHash, int ttlSeconds) {
    try {
      String json = objectMapper.writeValueAsString(new CodeEntry(codeHash, 0));
      redis.opsForValue().set(codeKey(emailHash), json, Duration.ofSeconds(ttlSeconds));
    } catch (Exception e) {
      throw new IllegalStateException("邮箱验证码写入 Redis 失败", e);
    }
  }

  public void deleteCode(String emailHash) {
    redis.delete(codeKey(emailHash));
  }

  public void deleteCooldown(String emailHash) {
    redis.delete(cooldownKey(emailHash));
  }

  public VerifyResult verify(String emailHash, String submittedHash, int maxAttempts) {
    List<?> result = redis.execute(
        VERIFY_SCRIPT,
        List.of(codeKey(emailHash)),
        submittedHash,
        String.valueOf(maxAttempts));
    String status = result == null || result.isEmpty()
        ? "EXPIRED" : String.valueOf(result.get(0));
    int attempts = result == null || result.size() < 2
        ? 0 : ((Number) result.get(1)).intValue();
    return new VerifyResult(
        "OK".equals(status),
        attempts,
        "LOCKED".equals(status));
  }

  private String codeKey(String emailHash) {
    return PREFIX + "code:" + emailHash;
  }

  private String cooldownKey(String emailHash) {
    return PREFIX + "cooldown:" + emailHash;
  }

  public record VerifyResult(boolean ok, int attempts, boolean locked) {
  }

  private record CodeEntry(String codeHash, int attempts) {
  }
}
