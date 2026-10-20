package com.shuati.abuse;

import com.shuati.common.ApiException;
import com.shuati.rate.RateLimiter;
import com.shuati.user.Profile;
import com.shuati.user.ProfileRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 新账号首日 AI 额度。
 *
 * <p>就算邮箱黑名单、图形验证码都被绕过，脚本也只能在头 24 小时里拿到有限次数的 AI 调用，
 * 收益远低于成本。窗口从「注册后第一次 AI 调用」起算，Redis 固定窗口即可。
 */
@Component
@RequiredArgsConstructor
public class NewAccountAiLimit {

  private final ProfileRepository profiles;
  private final RateLimiter rateLimiter;
  private final AbuseGuardProperties properties;

  public void check(String userId) {
    int limit = properties.getNewAccountAiDailyLimit();
    if (limit <= 0 || userId == null) {
      return;
    }
    LocalDateTime createdAt = profiles.findById(userId)
        .map(Profile::getCreatedAt)
        .orElse(null);
    if (createdAt == null) {
      return;
    }
    if (createdAt.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusHours(24))) {
      return;
    }
    RateLimiter.Decision decision = rateLimiter.allowWithRetry(
        "ai:new-account:" + userId, limit, Duration.ofHours(24));
    if (!decision.allowed()) {
      long seconds = Math.max(1, decision.retryAfterSeconds());
      throw new ApiException(
          429, "新账号首日 AI 次数已达上限（" + limit + " 次），请明天再试", seconds);
    }
  }
}
