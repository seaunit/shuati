package com.shuati.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis 邮箱验证码存储，验证码只保存哈希。 */
@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

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

  private static final DefaultRedisScript<Long> DELETE_ATTEMPT_SCRIPT =
      new DefaultRedisScript<>("""
          local value = redis.call('GET', KEYS[1])
          if not value then
            return 0
          end
          local entry = cjson.decode(value)
          if entry.codeHash == ARGV[1] and entry.nonce == ARGV[2] then
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
          end
          return 0
          """, Long.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public boolean acquireCooldown(
      String emailHash, EmailPurpose purpose, int seconds) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
        cooldownKey(emailHash, purpose), "1", Duration.ofSeconds(seconds)));
  }

  public String saveCode(
      String emailHash, EmailPurpose purpose, String codeHash, int ttlSeconds) {
    try {
      String nonce = UUID.randomUUID().toString();
      String json = objectMapper.writeValueAsString(new CodeEntry(codeHash, 0, nonce));
      redis.opsForValue().set(
          codeKey(emailHash, purpose), json, Duration.ofSeconds(ttlSeconds));
      return nonce;
    } catch (Exception e) {
      throw new IllegalStateException("邮箱验证码写入 Redis 失败", e);
    }
  }

  public void deleteCode(String emailHash, EmailPurpose purpose) {
    redis.delete(codeKey(emailHash, purpose));
  }

  public void deleteCooldown(String emailHash, EmailPurpose purpose) {
    redis.delete(cooldownKey(emailHash, purpose));
  }

  public long remainingCooldownSeconds(String emailHash, EmailPurpose purpose) {
    Long ttl = redis.getExpire(cooldownKey(emailHash, purpose), TimeUnit.SECONDS);
    return ttl == null || ttl < 1 ? 1 : ttl;
  }

  public boolean deleteAttempt(
      String emailHash, EmailPurpose purpose, String codeHash, String nonce) {
    Long deleted = redis.execute(
        DELETE_ATTEMPT_SCRIPT,
        List.of(codeKey(emailHash, purpose), cooldownKey(emailHash, purpose)),
        codeHash,
        nonce);
    return deleted != null && deleted == 1L;
  }

  public VerifyResult verify(
      String emailHash,
      EmailPurpose purpose,
      String submittedHash,
      int maxAttempts) {
    List<?> result = redis.execute(
        VERIFY_SCRIPT,
        List.of(codeKey(emailHash, purpose)),
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

  private String codeKey(String emailHash, EmailPurpose purpose) {
    return prefix(purpose) + "code:" + emailHash;
  }

  private String cooldownKey(String emailHash, EmailPurpose purpose) {
    return prefix(purpose) + "cooldown:" + emailHash;
  }

  private String prefix(EmailPurpose purpose) {
    return "email:" + purpose.name().toLowerCase(java.util.Locale.ROOT) + ":";
  }

  public record VerifyResult(boolean ok, int attempts, boolean locked) {
  }

  private record CodeEntry(String codeHash, int attempts, String nonce) {
  }
}
