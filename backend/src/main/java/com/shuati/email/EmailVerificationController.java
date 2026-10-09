package com.shuati.email;

import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.ClientInfo;
import com.shuati.rate.RateLimit;
import com.shuati.user.ProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
public class EmailVerificationController {

  private final ProfileRepository profiles;
  private final EmailVerificationService emails;

  @PostMapping("/email-code")
  @RateLimit(name = "auth:email-code:ip", limit = 30,
      windowSeconds = 3600, scope = RateLimit.Scope.IP)
  public ApiResponse<EmailVerificationService.SendResult> send(
      @Valid @RequestBody EmailCodeRequest request,
      HttpServletRequest http) {
    // 注册仅依赖邮箱验证码，不再要求图形验证码；发送频率由邮箱冷却与 IP/设备限流兜底。
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (profiles.findByEmail(email).isPresent()) {
      throw new ApiException(400, "该邮箱已注册，请直接登录");
    }
    return ApiResponse.ok(emails.sendRegisterCode(
        email, ClientInfo.ip(http), ClientInfo.device(http)));
  }
}
