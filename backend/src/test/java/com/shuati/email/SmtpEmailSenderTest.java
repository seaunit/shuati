package com.shuati.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class SmtpEmailSenderTest {

  @Autowired
  SmtpEmailSender sender;

  @MockitoBean
  JavaMailSender mailSender;

  @Test
  void sendsVerificationMailThroughConfiguredSender() {
    MimeMessage message = new MimeMessage(
        Session.getDefaultInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(message);
    doNothing().when(mailSender).send(any(MimeMessage.class));

    sender.sendRegisterCode("user@example.com", "123456");

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue()).isNotNull();
  }
}
