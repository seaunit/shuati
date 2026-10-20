package com.shuati.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuati.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class EmailDomainGuardTest {

  private final EmailDomainGuard guard = newGuard();

  private EmailDomainGuard newGuard() {
    try {
      return new EmailDomainGuard(
          new ClassPathResource("abuse/disposable-domains.txt"), new AbuseGuardProperties());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void blocksExactDomainIgnoringCase() {
    assertThat(guard.isBlocked("daniel.parkerca61598b@mailinator.com")).isTrue();
    assertThat(guard.isBlocked("X@Mailinator.COM")).isTrue();
    assertThat(guard.isBlocked("yopmail@YOPMAIL.FR")).isTrue();
  }

  @Test
  void blocksSubdomains() {
    assertThat(guard.isBlocked("a@b.mailinator.com")).isTrue();
    assertThat(guard.isBlocked("a@x.y.guerrillamail.com")).isTrue();
  }

  @Test
  void doesNotBlockLookalikeDomains() {
    assertThat(guard.isBlocked("a@notmailinator.com")).isFalse();
    assertThat(guard.isBlocked("a@mailinator.com.cn")).isFalse();
    assertThat(guard.isBlocked("a@myyopmail.com")).isFalse();
  }

  @Test
  void allowsCommonMailboxes() {
    assertThat(guard.isBlocked("a@qq.com")).isFalse();
    assertThat(guard.isBlocked("a@163.com")).isFalse();
    assertThat(guard.isBlocked("a@gmail.com")).isFalse();
    assertThat(guard.isBlocked("a@outlook.com")).isFalse();
    assertThat(guard.isBlocked("dev@seaunit.site")).isFalse();
  }

  @Test
  void normalizesTrailingDotAndWhitespace() {
    assertThat(guard.isBlocked("  a@mailinator.com.  ")).isTrue();
  }

  @Test
  void assertAllowedThrowsReadableError() {
    assertThatThrownBy(() -> guard.assertAllowed("a@mailinator.com", "1.2.3.4", "ua"))
        .isInstanceOf(ApiException.class)
        .hasMessage("请使用常用邮箱，临时邮箱暂不支持");
  }
}
