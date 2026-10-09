package com.shuati.password;

import com.shuati.captcha.CaptchaService;
import com.shuati.common.ApiResponse;
import com.shuati.common.ClientInfo;
import com.shuati.email.EmailVerificationService;
import com.shuati.rate.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final CaptchaService captchaService;
  private final PasswordResetService passwordResetService;

  @PostMapping("/email-code")
  @RateLimit(name = "auth:password-reset-code:ip", limit = 30,
      windowSeconds = 3600, scope = RateLimit.Scope.IP)
  public ApiResponse<EmailVerificationService.SendResult> sendCode(
      @Valid @RequestBody PasswordResetCodeRequest request,
      HttpServletRequest http) {
    captchaService.verify(request.captchaTicket(), request.captchaCode());
    return ApiResponse.ok(passwordResetService.sendCode(
        request.email(), ClientInfo.ip(http), ClientInfo.device(http)));
  }

  @PostMapping
  @RateLimit(name = "auth:password-reset:ip", limit = 10,
      windowSeconds = 3600, scope = RateLimit.Scope.IP)
  public ApiResponse<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.reset(
        request.email(), request.emailCode(), request.newPassword());
    return ApiResponse.ok(null);
  }
}
