package com.shuati.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
    return ResponseEntity.status(e.getStatus())
        .body(ApiResponse.fail(e.getStatus(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(err -> err.getField() + " " + err.getDefaultMessage())
        .orElse("参数校验失败");
    return ResponseEntity.status(400).body(ApiResponse.fail(400, message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
    log.error("未处理异常", e);
    return ResponseEntity.status(500).body(ApiResponse.fail(500, "服务器内部错误"));
  }
}
