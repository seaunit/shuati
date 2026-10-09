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
      String action = purpose == EmailPurpose.RESET ? "重置密码" : "注册";
      helper.setSubject("【拾题】" + action + "邮箱验证码");
      helper.setText(
          "您的" + action + "验证码是：" + code
              + "\n\n验证码 5 分钟内有效。若非本人操作，请忽略本邮件。",
          "<p>您的" + action + "验证码是：<strong>" + code + "</strong></p>"
              + "<p>验证码 5 分钟内有效。若非本人操作，请忽略本邮件。</p>");
      mailSender.send(message);
    } catch (Exception e) {
      throw new ApiException(503, "验证码邮件发送失败，请稍后重试");
    }
  }
}
