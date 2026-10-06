package com.shuati.auth;

import com.shuati.auth.dto.LoginRequest;
import com.shuati.auth.dto.RegisterRequest;
import com.shuati.auth.dto.UserView;
import com.shuati.common.ApiResponse;
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

  @Value("${shuati.cookie.secure:false}")
  private boolean cookieSecure;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<UserView>> register(
      @Valid @RequestBody RegisterRequest request) {
    return withAuthCookie(authService.register(request));
  }

  @PostMapping("/login")
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
