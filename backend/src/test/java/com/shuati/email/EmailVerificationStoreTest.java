package com.shuati.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class EmailVerificationStoreTest {

  @Autowired
  EmailVerificationStore store;

  @Test
  void cooldownOnlyAllowsFirstRequest() {
    String emailHash = "cooldown-" + UUID.randomUUID();
    assertThat(store.acquireCooldown(emailHash, 60)).isTrue();
    assertThat(store.acquireCooldown(emailHash, 60)).isFalse();
  }

  @Test
  void concurrentAcquireOnlyAllowsOneRequest() {
    String emailHash = "parallel-" + UUID.randomUUID();
    long allowed = IntStream.range(0, 10)
        .parallel()
        .filter(i -> store.acquireCooldown(emailHash, 60))
        .count();
    assertThat(allowed).isEqualTo(1);
  }

  @Test
  void correctCodeConsumesEntryImmediately() {
    String emailHash = "verify-" + UUID.randomUUID();
    store.saveCode(emailHash, "hash-1", 300);
    assertThat(store.verify(emailHash, "hash-1", 5).ok()).isTrue();
    assertThat(store.verify(emailHash, "hash-1", 5).ok()).isFalse();
  }

  @Test
  void concurrentVerifyOnlyAllowsOneSuccess() {
    String emailHash = "parallel-verify-" + UUID.randomUUID();
    store.saveCode(emailHash, "hash-parallel", 300);
    long allowed = IntStream.range(0, 10)
        .parallel()
        .filter(i -> store.verify(emailHash, "hash-parallel", 5).ok())
        .count();
    assertThat(allowed).isEqualTo(1);
  }

  @Test
  void fifthWrongAttemptLocksAndDeletesCode() {
    String emailHash = "lock-" + UUID.randomUUID();
    store.saveCode(emailHash, "hash-2", 300);
    for (int i = 1; i <= 5; i++) {
      EmailVerificationStore.VerifyResult result = store.verify(emailHash, "wrong", 5);
      assertThat(result.attempts()).isEqualTo(i);
      assertThat(result.ok()).isFalse();
      assertThat(result.locked()).isEqualTo(i == 5);
    }
    assertThat(store.verify(emailHash, "hash-2", 5).ok()).isFalse();
  }

  @Test
  void staleAttemptCannotDeleteNewerCodeOrCooldown() {
    String emailHash = "stale-" + UUID.randomUUID();
    String firstNonce = store.saveCode(emailHash, "first-hash", 300);
    store.acquireCooldown(emailHash, 60);

    String secondNonce = store.saveCode(emailHash, "second-hash", 300);
    store.acquireCooldown(emailHash, 60);

    assertThat(store.deleteAttempt(emailHash, "first-hash", firstNonce)).isFalse();
    assertThat(store.verify(emailHash, "second-hash", 5).ok()).isTrue();
    assertThat(secondNonce).isNotBlank();
  }
}
