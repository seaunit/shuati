package com.shuati.email;

import com.shuati.common.ApiException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;

  @Override
  public void sendCode(String email, String code, EmailPurpose purpose) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(
          properties.from(), properties.fromName(), StandardCharsets.UTF_8.name()));
      helper.setTo(email);
      VerificationEmailTemplate.Rendered rendered = VerificationEmailTemplate.render(
          purpose, code, properties.codeTtlSeconds());
      helper.setSubject(rendered.subject());
      helper.setText(rendered.text(), rendered.html());
      mailSender.send(message);
    } catch (Exception e) {
      throw new ApiException(503, "验证码邮件发送失败，请稍后重试");
    }
  }
}
