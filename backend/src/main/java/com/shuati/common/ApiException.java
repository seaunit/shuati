package com.shuati.common;

/**
 * 业务异常，由 GlobalExceptionHandler 转成统一响应体。
 */
public class ApiException extends RuntimeException {

  private final int status;
  private final long retryAfterSeconds;

  public ApiException(int status, String message) {
    this(status, message, 0);
  }

  public ApiException(int status, String message, long retryAfterSeconds) {
    super(message);
    this.status = status;
    this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
  }

  public int getStatus() {
    return status;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
