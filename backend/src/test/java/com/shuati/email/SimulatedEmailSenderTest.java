package com.shuati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

class SimulatedEmailSenderTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
      .withPropertyValues("shuati.email.simulate=true");

  @Test
  void simulateModeUsesLocalSenderInsteadOfSmtp() {
    runner.run(context -> {
      EmailSender sender = context.getBean(EmailSender.class);
      assertThat(sender.getClass().getSimpleName()).isEqualTo("SimulatedEmailSender");
      sender.sendCode("simulate@example.com", "123456", EmailPurpose.REGISTER);
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(EmailVerificationProperties.class)
  @Import({SimulatedEmailSender.class, SmtpEmailSender.class})
  static class TestConfig {
  }
}
