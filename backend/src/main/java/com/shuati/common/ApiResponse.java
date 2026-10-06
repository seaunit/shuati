package com.shuati.common;

/**
 * 统一响应体，保持与旧 Next.js 版本一致的 { code, message, data } 契约。
 */
public record ApiResponse<T>(int code, String message, T data) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(200, "ok", data);
  }

  public static <T> ApiResponse<T> fail(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }
}
