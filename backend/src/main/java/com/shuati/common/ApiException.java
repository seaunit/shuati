package com.shuati.common;

/**
 * 业务异常，由 GlobalExceptionHandler 转成统一响应体。
 */
public class ApiException extends RuntimeException {

  private final int status;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
