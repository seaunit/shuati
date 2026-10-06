package com.shuati.auth;

import com.shuati.auth.dto.LoginRequest;
import com.shuati.auth.dto.RegisterRequest;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.user.Profile;
import com.shuati.user.ProfileRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final ProfileRepository profiles;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final PointAccountService pointAccounts;

  @Transactional
  public LoginResult register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (profiles.findByEmail(email).isPresent()) {
      throw new ApiException(400, "该邮箱已注册，请直接登录");
    }

    Profile profile = new Profile();
    profile.setId(UUID.randomUUID().toString());
    profile.setEmail(email);
    profile.setPasswordHash(passwordEncoder.encode(request.password()));
    profile.setNickname(email.substring(0, email.indexOf('@')));
    profile.setRole("USER");
    profile.setStatus("ENABLED");
    // 同一事务内后面要用 JdbcTemplate 插 point_account，先 flush 保证外键可见
    profiles.saveAndFlush(profile);

    pointAccounts.ensureAccount(profile.getId());
    return new LoginResult(profile, jwtService.issue(profile.getId(), profile.getRole()));
  }

  @Transactional
  public LoginResult login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    Profile profile = profiles.findByEmail(email)
        .orElseThrow(() -> new ApiException(401, "邮箱或密码错误"));

    if ("DISABLED".equals(profile.getStatus())) {
      throw new ApiException(403, "账号已被禁用");
    }
    if (profile.getPasswordHash() == null
        || !passwordEncoder.matches(request.password(), profile.getPasswordHash())) {
      throw new ApiException(401, "邮箱或密码错误");
    }

    profile.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
    profiles.save(profile);
    return new LoginResult(profile, jwtService.issue(profile.getId(), profile.getRole()));
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  public record LoginResult(Profile profile, String token) {
  }
}
