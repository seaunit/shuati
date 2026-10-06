package com.shuati.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.common.ApiException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class CaptchaServiceTest {

  @Autowired
  CaptchaService captchaService;

  @Autowired
  CaptchaStore captchaStore;

  @Autowired
  StringRedisTemplate redis;

  @Autowired
  ObjectMapper objectMapper;

  @BeforeEach
  void cleanRedis() {
    deleteByPrefix("captcha:*");
    deleteByPrefix("rate:*");
  }

  private void deleteByPrefix(String pattern) {
    Set<String> keys = redis.keys(pattern);
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  @Test
  void generatedCodeUsesSafeCharset() {
    SecureSpecCaptcha captcha = new SecureSpecCaptcha(150, 48, 4);
    String code = captcha.text();
    assertThat(code).hasSize(4);
    // 不含 0 / O / 1 / l / I
    assertThat(code).matches("[23456789A-HJ-NP-Z]{4}");
  }

  @Test
  void imageResponseHasTicketAndBase64ButNoAnswer() throws Exception {
    CaptchaImageResponse image = captchaService.generate("1.1.1.1", "device-a");

    assertThat(image.ticket()).isNotBlank();
    assertThat(image.imageBase64()).startsWith("data:image/png;base64,");
    assertThat(image.expiresInSeconds()).isEqualTo(120);

    // 接口响应里没有任何答案字段
    assertThat(image.toString()).doesNotContain(extractCode(image.ticket()));
  }

  @Test
  void verifySucceedsOnceThenCodeIsDestroyed() throws Exception {
    CaptchaImageResponse image = captchaService.generate("2.2.2.2", "device-b");
    String code = extractCode(image.ticket());

    captchaService.verify(image.ticket(), code);
    assertThat(captchaStore.rawEntry(image.ticket())).isNull();

    assertThatThrownBy(() -> captchaService.verify(image.ticket(), code))
        .isInstanceOf(ApiException.class)
        .hasMessage(CaptchaService.UNIFORM_ERROR);
  }

  @Test
  void wrongInputLocksAndDestroysAfterMaxAttempts() throws Exception {
    CaptchaImageResponse image = captchaService.generate("3.3.3.3", "device-c");
    String code = extractCode(image.ticket());
    String wrong = "ZZZZ".equals(code) ? "YYYY" : "ZZZZ";

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> captchaService.verify(image.ticket(), wrong))
          .isInstanceOf(ApiException.class)
          .hasMessage(CaptchaService.UNIFORM_ERROR);
    }
    // 达到上限后验证码被直接作废
    assertThat(captchaStore.rawEntry(image.ticket())).isNull();
  }

  @Test
  void unknownOrExpiredTicketReturnsUniformError() {
    assertThatThrownBy(() -> captchaService.verify("not-exist-ticket", "ABCD"))
        .isInstanceOf(ApiException.class)
        .hasMessage(CaptchaService.UNIFORM_ERROR);
  }

  @Test
  void rateLimitBlocksRepeatedRequests() {
    String ip = "9.9.9.9";
    String device = "device-limit";
    for (int i = 0; i < 5; i++) {
      captchaService.generate(ip, device);
    }
    assertThatThrownBy(() -> captchaService.generate(ip, device))
        .isInstanceOf(ApiException.class)
        .hasMessage("请求过于频繁，请稍后再试");
  }

  private String extractCode(String ticket) throws Exception {
    String json = captchaStore.rawEntry(ticket);
    return objectMapper.readTree(json).path("code").asText();
  }
}
