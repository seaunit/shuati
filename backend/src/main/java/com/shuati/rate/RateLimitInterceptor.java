package com.shuati.rate;

import com.shuati.common.ApiException;
import com.shuati.common.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器：
 * <ol>
 *   <li>全局兜底：同一 IP 每分钟的请求总量（拦脚本扫描）；</li>
 *   <li>接口级：读取 {@link RateLimit} 注解，按 IP / 用户维度限流。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  private final RateLimiter rateLimiter;
  private final RateLimitProperties properties;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!properties.isEnabled()) {
      return true;
    }

    String ip = ClientInfo.ip(request);
    String path = request.getRequestURI();

    // 1) 全局兜底（只兜业务接口，静态资源不在此列）
    if (properties.getGlobalPerMinute() > 0
        && (path.startsWith("/api/") || path.startsWith("/captcha/"))) {
      if (!rateLimiter.allow("global:ip:" + ip, properties.getGlobalPerMinute(), ONE_MINUTE)) {
        throw new ApiException(429, "请求过于频繁，请稍后再试");
      }
    }

    // 2) 接口级限流
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    RateLimit annotation = method.getMethodAnnotation(RateLimit.class);
    if (annotation == null) {
      return true;
    }
    Duration window = Duration.ofSeconds(Math.max(1, annotation.windowSeconds()));
    String userId = currentUserId();

    boolean needIp = annotation.scope() == RateLimit.Scope.IP
        || annotation.scope() == RateLimit.Scope.IP_AND_USER;
    boolean needUser = annotation.scope() == RateLimit.Scope.USER
        || annotation.scope() == RateLimit.Scope.IP_AND_USER;

    if (needIp && !rateLimiter.allow(
        annotation.name() + ":ip:" + ip, annotation.limit(), window)) {
      throw new ApiException(429, "请求过于频繁，请稍后再试");
    }
    // 未登录时 USER 维度退化为 IP，避免匿名请求绕过
    String userKey = userId == null ? "ip:" + ip : "user:" + userId;
    if (needUser && !rateLimiter.allow(
        annotation.name() + ":" + userKey, annotation.limit(), window)) {
      throw new ApiException(429, "请求过于频繁，请稍后再试");
    }
    return true;
  }

  private String currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    return String.valueOf(auth.getPrincipal());
  }
}
