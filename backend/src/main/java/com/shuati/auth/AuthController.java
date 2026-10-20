package com.shuati.auth;

import com.shuati.auth.dto.LoginRequest;
import com.shuati.auth.dto.RegisterRequest;
import com.shuati.auth.dto.UserView;
import com.shuati.abuse.RegisterThrottle;
import com.shuati.common.ApiResponse;
import com.shuati.common.ClientInfo;
import com.shuati.rate.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RegisterThrottle registerThrottle;

  @Value("${shuati.cookie.secure:false}")
  private boolean cookieSecure;

  @PostMapping("/register")
  // 注册：同一 IP 小时级 + 日级双限（RegisterThrottle，参数见 shuati.abuse.*）
  public ResponseEntity<ApiResponse<UserView>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    String ip = ClientInfo.ip(http);
    registerThrottle.checkIp(ip);
    return withAuthCookie(authService.register(request, ip, http.getHeader("User-Agent")));
  }

  @PostMapping("/login")
  // 登录：同一 IP 每分钟最多 10 次（配合验证码，挡住撞库）
  @RateLimit(name = "auth:login", limit = 10, windowSeconds = 60, scope = RateLimit.Scope.IP)
  public ResponseEntity<ApiResponse<UserView>> login(
      @Valid @RequestBody LoginRequest request) {
    return withAuthCookie(authService.login(request));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout() {
    ResponseCookie cookie = baseCookie("").maxAge(0).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiResponse.ok(null));
  }

  private ResponseEntity<ApiResponse<UserView>> withAuthCookie(AuthService.LoginResult result) {
    ResponseCookie cookie = baseCookie(result.token()).maxAge(Duration.ofDays(7)).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiResponse.ok(UserView.from(result.profile())));
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/");
  }
}
