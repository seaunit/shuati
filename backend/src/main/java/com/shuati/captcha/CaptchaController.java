package com.shuati.captcha;

import com.shuati.common.ApiResponse;
import com.shuati.common.ClientInfo;
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
    return ApiResponse.ok(
        captchaService.generate(ClientInfo.ip(request), ClientInfo.device(request)));
  }

  /** 校验图形验证码：一次性，成功后立即作废。 */
  @PostMapping("/captcha/verify")
  public ApiResponse<Map<String, Object>> verify(@RequestBody CaptchaVerifyRequest body) {
    captchaService.verify(body.ticket(), body.userInputCode());
    return ApiResponse.ok(Map.of("valid", true));
  }

}
