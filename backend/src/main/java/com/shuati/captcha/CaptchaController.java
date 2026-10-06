package com.shuati.captcha;

import com.shuati.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CaptchaController {

  private final CaptchaService captchaService;

  /** 获取图形验证码：返回 Base64 图片 + ticket，不返回答案。 */
  @GetMapping("/captcha/image")
  public ApiResponse<CaptchaImageResponse> image(HttpServletRequest request) {
    return ApiResponse.ok(captchaService.generate(clientIp(request), deviceId(request)));
  }

  /** 校验图形验证码：一次性，成功后立即作废。 */
  @PostMapping("/captcha/verify")
  public ApiResponse<Map<String, Object>> verify(@RequestBody CaptchaVerifyRequest body) {
    captchaService.verify(body.ticket(), body.userInputCode());
    return ApiResponse.ok(Map.of("valid", true));
  }

  private String clientIp(HttpServletRequest request) {
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

  private String deviceId(HttpServletRequest request) {
    String device = request.getHeader("X-Device-Id");
    if (device != null && !device.isBlank()) {
      return device.trim();
    }
    String agent = request.getHeader("User-Agent");
    return agent == null ? "unknown" : Integer.toHexString(agent.hashCode());
  }
}
