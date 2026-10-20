package com.shuati.abuse;

import com.shuati.common.ApiException;
import com.shuati.rate.RateLimiter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 注册频率闸：同一 IP 的小时级 + 日级上限。
 *
 * <p>域名黑名单会被换域名绕过，IP 频率不会——脚本要换 IP 就是真金白银的成本。
 * 计数走 Redis（{@link RateLimiter}），多实例天然共享。
 */
@Component
@RequiredArgsConstructor
public class RegisterThrottle {

  private final RateLimiter rateLimiter;
  private final AbuseGuardProperties properties;

  public void checkIp(String ip) {
    String key = ip == null || ip.isBlank() ? "unknown" : ip.trim();
    RateLimiter.Decision hourly = rateLimiter.allowWithRetry(
        "reg:hourly:ip:" + key, properties.getRegisterIpHourlyLimit(), Duration.ofHours(1));
    if (!hourly.allowed()) {
      throw tooMany(hourly);
    }
    RateLimiter.Decision daily = rateLimiter.allowWithRetry(
        "reg:daily:ip:" + key + ":" + LocalDate.now(ZoneOffset.UTC),
        properties.getRegisterIpDailyLimit(),
        Duration.ofDays(1));
    if (!daily.allowed()) {
      throw tooMany(daily);
    }
  }

  private ApiException tooMany(RateLimiter.Decision decision) {
    long seconds = Math.max(1, decision.retryAfterSeconds());
    return new ApiException(429, "注册过于频繁，请 " + seconds + " 秒后再试", seconds);
  }
}
