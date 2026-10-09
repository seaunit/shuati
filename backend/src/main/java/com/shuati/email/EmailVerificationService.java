package com.shuati.email;

import com.shuati.common.ApiException;
import com.shuati.rate.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter HOUR =
      DateTimeFormatter.ofPattern("yyyyMMddHH");

  private final EmailVerificationStore store;
  private final EmailSender sender;
  private final EmailVerificationProperties properties;
  private final RateLimiter rateLimiter;

  public SendResult sendRegisterCode(String email, String ip, String device) {
    return sendCode(email, ip, device, EmailPurpose.REGISTER);
  }

  public SendResult sendCode(
      String email, String ip, String device, EmailPurpose purpose) {
    if (!properties.enabled()) {
      throw new ApiException(503, "邮箱验证暂不可用");
    }
    String normalized = normalize(email);
    if (normalized.isBlank()) {
      throw new ApiException(400, "请填写邮箱");
    }

    String emailHash = emailHash(normalized);
    SendResult allowance = consumeAllowance(email, ip, device, purpose);
    if (!allowance.codeAllowed()) {
      return allowance;
    }

    String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
    String codeHash = codeHash(normalized, code, purpose);
    String nonce = store.saveCode(
        emailHash, purpose, codeHash, properties.codeTtlSeconds());
    try {
      sender.sendCode(normalized, code, purpose);
      return new SendResult(properties.cooldownSeconds(), properties.codeTtlSeconds());
    } catch (ApiException e) {
      store.deleteAttempt(emailHash, purpose, codeHash, nonce);
      throw e;
    } catch (Exception e) {
      store.deleteAttempt(emailHash, purpose, codeHash, nonce);
      throw new ApiException(503, "验证码邮件发送失败，请稍后重试");
    }
  }

  public void verifyRegisterCode(String email, String code) {
    verifyCode(email, code, EmailPurpose.REGISTER);
  }

  public void verifyCode(String email, String code, EmailPurpose purpose) {
    String normalized = normalize(email);
    if (normalized.isBlank() || code == null || !code.matches("\\d{6}")) {
      throw new ApiException(400, "验证码错误或已过期");
    }
    EmailVerificationStore.VerifyResult result = store.verify(
        emailHash(normalized),
        purpose,
        codeHash(normalized, code, purpose),
        properties.maxAttempts());
    if (!result.ok()) {
      throw new ApiException(400, "验证码错误或已过期");
    }
  }

  String emailHash(String email) {
    return hash("email:" + normalize(email));
  }

  String codeHash(String email, String code, EmailPurpose purpose) {
    return hash("code:" + purpose.name() + ":" + normalize(email) + ":" + code);
  }

  public SendResult consumeAllowance(
      String email, String ip, String device, EmailPurpose purpose) {
    if (!properties.enabled()) {
      throw new ApiException(503, "邮箱验证暂不可用");
    }
    String normalized = normalize(email);
    String emailHash = emailHash(normalized);
    if (!store.acquireCooldown(emailHash, purpose, properties.cooldownSeconds())) {
      long seconds = store.remainingCooldownSeconds(emailHash, purpose);
      throw new ApiException(
          429, "验证码发送过于频繁，请 " + seconds + " 秒后重试", seconds);
    }

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String p = purpose.name().toLowerCase(Locale.ROOT);
    checkLimit(
        "email:" + p + ":email:" + emailHash + ":" + now.format(DAY),
        properties.emailDailyLimit(),
        Duration.ofDays(1));
    checkLimit(
        "email:" + p + ":ip:" + safe(ip) + ":" + now.format(HOUR),
        properties.ipHourlyLimit(),
        Duration.ofHours(1));
    checkLimit(
        "email:" + p + ":device:" + hash("device:" + safe(device))
            + ":" + now.format(DAY),
        properties.deviceDailyLimit(),
        Duration.ofDays(1));
    checkLimit(
        "email:" + p + ":global:" + now.format(DAY),
        properties.globalDailyLimit(),
        Duration.ofDays(1));
    return new SendResult(
        properties.cooldownSeconds(), properties.codeTtlSeconds(), true);
  }

  private void checkLimit(String key, int limit, Duration window) {
    RateLimiter.Decision decision = rateLimiter.allowWithRetry(key, limit, window);
    if (!decision.allowed()) {
      long seconds = Math.max(1, decision.retryAfterSeconds());
      throw new ApiException(
          429, "验证码发送过于频繁，请 " + seconds + " 秒后重试", seconds);
    }
  }

  private String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value.trim();
  }

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((properties.secret() + ":" + value).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  public record SendResult(
      int cooldownSeconds,
      int expiresInSeconds,
      boolean codeAllowed) {

    public SendResult(int cooldownSeconds, int expiresInSeconds) {
      this(cooldownSeconds, expiresInSeconds, true);
    }
  }
}
