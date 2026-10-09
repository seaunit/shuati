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
    seed(store, service, properties, email, code, EmailPurpose.REGISTER);
  }

  public static void seed(
      EmailVerificationStore store,
      EmailVerificationService service,
      EmailVerificationProperties properties,
      String email,
      String code,
      EmailPurpose purpose) {
    store.saveCode(
        service.emailHash(email),
        purpose,
        service.codeHash(email, code, purpose),
        properties.codeTtlSeconds());
  }
}
