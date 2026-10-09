package com.shuati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.shuati.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationServiceTest {

  @Autowired
  EmailVerificationService service;

  @MockitoBean
  EmailSender sender;

  @BeforeEach
  void resetSender() {
    reset(sender);
    doNothing().when(sender).sendCode(anyString(), anyString(), any());
  }

  @Test
  void secondSendWithinCooldownIsRejected() {
    service.sendCode(
        "one@example.com", "1.1.1.1", "device-1", EmailPurpose.REGISTER);
    assertThatThrownBy(() ->
        service.sendCode(
            "one@example.com", "1.1.1.1", "device-1", EmailPurpose.REGISTER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("频繁");
  }

  @Test
  void correctCodeCannotBeReused() {
    String email = "two@example.com";
    service.sendCode(email, "1.1.1.2", "device-2", EmailPurpose.REGISTER);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(sender).sendCode(eq(email), code.capture(), eq(EmailPurpose.REGISTER));

    service.verifyCode(email, code.getValue(), EmailPurpose.REGISTER);
    assertThatThrownBy(() ->
        service.verifyCode(email, code.getValue(), EmailPurpose.REGISTER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("验证码错误");
  }

  @Test
  void smtpFailureClearsCodeAndCooldownButKeepsCounters() {
    String email = "three@example.com";
    doThrow(new ApiException(503, "send failed"))
        .when(sender).sendCode(anyString(), anyString(), any());

    assertThatThrownBy(() ->
        service.sendCode(email, "1.1.1.3", "device-3", EmailPurpose.REGISTER))
        .isInstanceOf(ApiException.class);

    doNothing().when(sender).sendCode(anyString(), anyString(), any());
    service.sendCode(email, "1.1.1.3", "device-3", EmailPurpose.REGISTER);
  }

  @Test
  void registerCodeCannotBeUsedForReset() {
    String email = "purpose-register@example.com";
    service.sendCode(email, "1.1.1.10", "device-purpose-1", EmailPurpose.REGISTER);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(sender).sendCode(eq(email), code.capture(), eq(EmailPurpose.REGISTER));

    assertThatThrownBy(() ->
        service.verifyCode(email, code.getValue(), EmailPurpose.RESET))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("验证码错误");
  }

  @Test
  void resetCodeCannotBeUsedForRegister() {
    String email = "purpose-reset@example.com";
    service.sendCode(email, "1.1.1.11", "device-purpose-2", EmailPurpose.RESET);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(sender).sendCode(eq(email), code.capture(), eq(EmailPurpose.RESET));

    assertThatThrownBy(() ->
        service.verifyCode(email, code.getValue(), EmailPurpose.REGISTER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("验证码错误");
  }
}
