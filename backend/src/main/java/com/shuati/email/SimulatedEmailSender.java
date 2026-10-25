package com.shuati.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 本地测试专用邮件模拟器：验证码写入后端日志，不连接 SMTP。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "shuati.email",
    name = "simulate",
    havingValue = "true")
public class SimulatedEmailSender implements EmailSender {

  private final EmailVerificationProperties properties;

  @Override
  public void sendCode(String email, String code, EmailPurpose purpose) {
    log.info(
        "[模拟邮件] to={} purpose={} code={} expiresIn={}s",
        email, purpose, code, properties.codeTtlSeconds());
  }
}
