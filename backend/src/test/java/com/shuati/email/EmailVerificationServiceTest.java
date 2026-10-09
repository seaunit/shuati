package com.shuati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    doNothing().when(sender).sendRegisterCode(anyString(), anyString());
  }

  @Test
  void secondSendWithinCooldownIsRejected() {
    service.sendRegisterCode("one@example.com", "1.1.1.1", "device-1");
    assertThatThrownBy(() ->
        service.sendRegisterCode("one@example.com", "1.1.1.1", "device-1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("频繁");
  }

  @Test
  void correctCodeCannotBeReused() {
    String email = "two@example.com";
    service.sendRegisterCode(email, "1.1.1.2", "device-2");
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(sender).sendRegisterCode(eq(email), code.capture());

    service.verifyRegisterCode(email, code.getValue());
    assertThatThrownBy(() -> service.verifyRegisterCode(email, code.getValue()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("验证码错误");
  }

  @Test
  void smtpFailureClearsCodeAndCooldownButKeepsCounters() {
    String email = "three@example.com";
    doThrow(new ApiException(503, "send failed"))
        .when(sender).sendRegisterCode(anyString(), anyString());

    assertThatThrownBy(() ->
        service.sendRegisterCode(email, "1.1.1.3", "device-3"))
        .isInstanceOf(ApiException.class);

    doNothing().when(sender).sendRegisterCode(anyString(), anyString());
    service.sendRegisterCode(email, "1.1.1.3", "device-3");
  }
}
