package com.shuati.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

  private CurrentUser() {
  }

  public static String id() {
    String id = idOrNull();
    if (id == null) {
      throw new ApiException(401, "请先登录");
    }
    return id;
  }

  /**
   * 游客模式下用来读取公开数据：未登录返回 null，由调用方决定可见范围。
   */
  public static String idOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    return String.valueOf(auth.getPrincipal());
  }

  public static boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }
}
