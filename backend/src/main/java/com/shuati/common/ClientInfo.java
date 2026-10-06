package com.shuati.common;

import jakarta.servlet.http.HttpServletRequest;

/** 从请求里提取客户端 IP / 设备标识，供限流与验证码共用。 */
public final class ClientInfo {

  private ClientInfo() {
  }

  public static String ip(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }

  public static String device(HttpServletRequest request) {
    String device = request.getHeader("X-Device-Id");
    if (device != null && !device.isBlank()) {
      return device.trim();
    }
    String agent = request.getHeader("User-Agent");
    return agent == null ? "unknown" : Integer.toHexString(agent.hashCode());
  }
}
