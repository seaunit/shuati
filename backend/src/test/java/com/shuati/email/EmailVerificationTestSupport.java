package com.shuati.email;

public final class EmailVerificationTestSupport {

  private EmailVerificationTestSupport() {
  }

  public static void seed(
      EmailVerificationStore store,
      EmailVerificationService service,
      EmailVerificationProperties properties,
      String email,
      String code) {
    store.saveCode(
        service.emailHash(email),
        service.codeHash(email, code),
        properties.codeTtlSeconds());
  }
}
