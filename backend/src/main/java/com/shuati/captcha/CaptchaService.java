package com.shuati.captcha;

import com.shuati.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 验证码业务：生成、存储、校验。
 *
 * <p>安全要点：
 * <ul>
 *   <li>答案只在服务端生成与保存，接口永远不返回明文；</li>
 *   <li>Redis TTL 自动过期；校验成功立即删除，保证一次性；</li>
 *   <li>默认加盐哈希存储，即使 Redis 被读也无法直接还原答案；</li>
 *   <li>错误提示统一，不区分不存在 / 过期 / 输错。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

  /** 统一错误提示：任何校验失败都返回这一句，防止黑产探测 */
  public static final String UNIFORM_ERROR = "验证码错误或已过期";

  private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  private final CaptchaStore store;
  private final CaptchaProperties properties;

  public CaptchaImageResponse generate(String clientIp, String deviceId) {
    checkRateLimit("ip", clientIp, properties.getIpLimitPerMinute());
    checkRateLimit("device", deviceId, properties.getDeviceLimitPerMinute());

    SecureSpecCaptcha captcha = new SecureSpecCaptcha(
        properties.getWidth(), properties.getHeight(), properties.getCodeLength());
    ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
    if (!captcha.out(imageBytes)) {
      throw new ApiException(500, "验证码生成失败，请稍后重试");
    }

    String code = captcha.text().toUpperCase(Locale.ROOT);
    String ticket = UUID.randomUUID().toString().replace("-", "");
    store.save(ticket, protect(code), Duration.ofSeconds(properties.getTtlSeconds()));

    String imageBase64 =
        "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes.toByteArray());
    return new CaptchaImageResponse(ticket, imageBase64, properties.getTtlSeconds());
  }

  /** 校验成功后验证码立即作废；任何失败都抛统一提示。 */
  public void verify(String ticket, String userInputCode) {
    if (ticket == null || ticket.isBlank() || userInputCode == null || userInputCode.isBlank()) {
      throw new ApiException(400, UNIFORM_ERROR);
    }
    String submitted = protect(userInputCode.trim().toUpperCase(Locale.ROOT));
    List<?> result = store.verify(ticket, submitted, properties.getMaxAttempts());
    String status = (result == null || result.isEmpty())
        ? "EXPIRED" : String.valueOf(result.get(0));
    if (!"OK".equals(status)) {
      throw new ApiException(400, UNIFORM_ERROR);
    }
  }

  private void checkRateLimit(String dimension, String value, int limit) {
    if (value == null || value.isBlank() || limit <= 0) {
      return;
    }
    long count = store.hitRate(dimension, value, RATE_WINDOW);
    if (count > limit) {
      throw new ApiException(429, "请求过于频繁，请稍后再试");
    }
  }

  /** 明文或加盐 SHA-256。哈希是确定性的，便于比对，同时避免明文落盘。 */
  private String protect(String code) {
    if (!properties.isHashStore()) {
      return code;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(
          (properties.getSecret() + ":" + code).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }
}
