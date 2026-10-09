package com.shuati.password;

import com.shuati.common.ApiException;
import com.shuati.email.EmailPurpose;
import com.shuati.email.EmailVerificationService;
import com.shuati.user.Profile;
import com.shuati.user.ProfileRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private final ProfileRepository profiles;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emails;

  public EmailVerificationService.SendResult sendCode(
      String email, String ip, String device) {
    String normalized = normalize(email);
    if (profiles.findByEmail(normalized).isEmpty()) {
      return emails.consumeAllowance(normalized, ip, device, EmailPurpose.RESET);
    }
    return emails.sendCode(normalized, ip, device, EmailPurpose.RESET);
  }

  @Transactional
  public void reset(String email, String emailCode, String newPassword) {
    String normalized = normalize(email);
    Profile profile = profiles.findByEmail(normalized)
        .orElseThrow(() -> new ApiException(400, "验证码错误或已过期"));
    emails.verifyCode(normalized, emailCode, EmailPurpose.RESET);
    profile.setPasswordHash(passwordEncoder.encode(newPassword));
    profile.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
    profile.setSessionVersion(profile.getSessionVersion() + 1);
    profiles.save(profile);
  }

  private String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
