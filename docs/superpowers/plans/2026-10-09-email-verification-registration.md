# 注册邮箱验证码 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 注册时必须通过阿里云 DirectMail 邮箱验证码，且具备邮箱、IP、设备、全站多维防刷和一次性验证码保护。

**Architecture:** 保留现有 Spring Boot + Redis + Vue 架构。新增邮件验证码服务与 SMTP 发信适配器，Redis 负责验证码、冷却和限额，注册接口在创建账号前原子消费验证码。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Mail, MySQL/Flyway, Redis, Vue 3, Playwright, Alibaba Cloud DirectMail SMTP.

**Spec:** `docs/superpowers/specs/2026-10-09-email-verification-registration-design.md`

## Global Constraints

- 邮件服务固定为阿里云 DirectMail：`smtpdm.aliyun.com`、`465`、`SSL`、`no-reply@mail.seaunit.site`。
- 同邮箱 60 秒只能发送一次；同邮箱每日 10 次；同 IP 每小时 20 次；同设备每日 30 次；全站每日 2000 次。
- 验证码为 6 位数字，TTL 300 秒，最多错误 5 次，成功后立即删除。
- Redis 只存验证码哈希，不存明文；响应和日志不得输出验证码。
- SMTP 密码只存 `/etc/shuati/env`，不进入 Git。
- Redis 不可用时邮箱验证码功能 fail-closed。
- 现有登录、图形验证码、点数和支付逻辑不得回归。

## Review Focus

- 两个并发发送请求必须只有一个通过 60 秒冷却。
- SMTP 超时后不能留下可再次使用的验证码，也不能回滚已消耗的防刷限额。
- 两个并发注册请求不能同时消费同一个验证码。
- 用户发送后修改邮箱，旧验证码不能用于新邮箱。
- Cloudflare 代理下 IP 限额必须使用真实用户 IP，而不是 Cloudflare 节点 IP。

---

### Task 1: 邮件配置、实体字段与 Flyway 迁移

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V9__email_verified_at.sql`
- Modify: `backend/src/main/java/com/shuati/user/Profile.java`
- Modify: `backend/src/main/java/com/shuati/config/AdminBootstrap.java`
- Create: `backend/src/main/java/com/shuati/email/EmailVerificationProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `deploy/env.example`

**Interfaces:**
- Consumes: Spring Boot configuration binding and existing `Profile` entity.
- Produces: `EmailVerificationProperties` getters for enabled, TTL, cooldown, limits, sender and secret.

- [ ] **Step 1: Add dependency and write migration**

Add to `backend/pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

Create `V9__email_verified_at.sql`:

```sql
ALTER TABLE profiles
  ADD COLUMN email_verified_at datetime(6) NULL AFTER status;
```

- [ ] **Step 2: Add the entity field and bootstrap timestamp**

Add to `Profile`:

```java
@Column(name = "email_verified_at")
private LocalDateTime emailVerifiedAt;
```

Update `AdminBootstrap` insert:

```java
jdbc.update("""
    insert into profiles
      (id, email, password_hash, nickname, role, status, email_verified_at)
    values (?, ?, ?, ?, 'ADMIN', 'ENABLED', now(6))
    """, id, normalized, passwordEncoder.encode(password),
    normalized.substring(0, normalized.indexOf('@')));
```

- [ ] **Step 3: Add configuration properties**

Create `EmailVerificationProperties`:

```java
package com.shuati.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shuati.email")
public record EmailVerificationProperties(
    boolean enabled,
    String from,
    String fromName,
    String secret,
    int codeTtlSeconds,
    int cooldownSeconds,
    int maxAttempts,
    int emailDailyLimit,
    int ipHourlyLimit,
    int deviceDailyLimit,
    int globalDailyLimit) {
}
```

Add Spring mail and custom properties to `application.yml`:

```yaml
spring:
  mail:
    host: ${SHUATI_MAIL_HOST:}
    port: ${SHUATI_MAIL_PORT:465}
    username: ${SHUATI_MAIL_USERNAME:}
    password: ${SHUATI_MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: ${SHUATI_MAIL_SSL:true}
      mail.smtp.connectiontimeout: ${SHUATI_MAIL_CONNECT_TIMEOUT_MS:10000}
      mail.smtp.timeout: ${SHUATI_MAIL_READ_TIMEOUT_MS:10000}

shuati:
  email:
    enabled: ${SHUATI_MAIL_ENABLED:false}
    from: ${SHUATI_MAIL_FROM:}
    from-name: ${SHUATI_MAIL_FROM_NAME:拾题}
    secret: ${SHUATI_EMAIL_SECRET:}
    code-ttl-seconds: ${SHUATI_MAIL_CODE_TTL_SECONDS:300}
    cooldown-seconds: ${SHUATI_MAIL_COOLDOWN_SECONDS:60}
    max-attempts: 5
    email-daily-limit: ${SHUATI_MAIL_EMAIL_DAILY_LIMIT:10}
    ip-hourly-limit: ${SHUATI_MAIL_IP_HOURLY_LIMIT:20}
    device-daily-limit: ${SHUATI_MAIL_DEVICE_DAILY_LIMIT:30}
    global-daily-limit: ${SHUATI_MAIL_GLOBAL_DAILY_LIMIT:2000}
```

Add test values in `application-test.yml`:

```yaml
shuati:
  email:
    enabled: true
    from: no-reply@example.com
    from-name: 拾题测试
    secret: test-email-secret
    code-ttl-seconds: 300
    cooldown-seconds: 60
    max-attempts: 5
    email-daily-limit: 10
    ip-hourly-limit: 20
    device-daily-limit: 30
    global-daily-limit: 2000
```

Add all corresponding `SHUATI_MAIL_*` and `SHUATI_EMAIL_SECRET` entries to `deploy/env.example`.

- [ ] **Step 4: Enable configuration properties**

Annotate `ShuatiApplication`:

```java
@EnableConfigurationProperties(EmailVerificationProperties.class)
```

- [ ] **Step 5: Run the existing test suite**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: PASS. Flyway validates `V9` and AdminBootstrap tests still pass.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/db/migration/V9__email_verified_at.sql backend/src/main/java/com/shuati/user/Profile.java backend/src/main/java/com/shuati/config/AdminBootstrap.java backend/src/main/java/com/shuati/email/EmailVerificationProperties.java backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml deploy/env.example
git commit -m "feat(auth): 增加邮箱验证配置与数据库字段"
```

---

### Task 2: Redis 验证码存储与原子校验

**Files:**
- Create: `backend/src/main/java/com/shuati/email/EmailVerificationStore.java`
- Create: `backend/src/test/java/com/shuati/email/EmailVerificationStoreTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`, `EmailVerificationProperties`.
- Produces:
  - `boolean acquireCooldown(String emailHash, int seconds)`
  - `void saveCode(String emailHash, String codeHash, int ttlSeconds)`
  - `void deleteCode(String emailHash)`
  - `void deleteCooldown(String emailHash)`
  - `VerifyResult verify(String emailHash, String submittedHash, int maxAttempts)`

`VerifyResult` is:

```java
public record VerifyResult(boolean ok, int attempts, boolean locked) {}
```

- [ ] **Step 1: Write failing store tests**

Create `EmailVerificationStoreTest`:

```java
@ActiveProfiles("test")
@SpringBootTest
class EmailVerificationStoreTest {

  @Autowired EmailVerificationStore store;

  @Test
  void cooldownOnlyAllowsFirstRequest() {
    String emailHash = "cooldown-" + UUID.randomUUID();
    assertThat(store.acquireCooldown(emailHash, 60)).isTrue();
    assertThat(store.acquireCooldown(emailHash, 60)).isFalse();
  }

  @Test
  void correctCodeConsumesEntryImmediately() {
    String emailHash = "verify-" + UUID.randomUUID();
    store.saveCode(emailHash, "hash-1", 300);
    assertThat(store.verify(emailHash, "hash-1", 5).ok()).isTrue();
    assertThat(store.verify(emailHash, "hash-1", 5).ok()).isFalse();
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
  }

  @Test
  void concurrentAcquireOnlyAllowsOneRequest() {
    String emailHash = "parallel-" + UUID.randomUUID();
    long allowed = java.util.stream.IntStream.range(0, 10)
        .parallel()
        .filter(i -> store.acquireCooldown(emailHash, 60))
        .count();
    assertThat(allowed).isEqualTo(1);
  }

  @Test
  void concurrentVerifyOnlyAllowsOneSuccess() {
    String emailHash = "parallel-verify-" + UUID.randomUUID();
    store.saveCode(emailHash, "hash-parallel", 300);
    long allowed = java.util.stream.IntStream.range(0, 10)
        .parallel()
        .filter(i -> store.verify(emailHash, "hash-parallel", 5).ok())
        .count();
    assertThat(allowed).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EmailVerificationStoreTest test
```

Expected: compilation FAIL because `EmailVerificationStore` does not exist.

- [ ] **Step 3: Implement the store**

Create `EmailVerificationStore` with keys:

```text
email:register:code:{emailHash}
email:register:cooldown:{emailHash}
```

Implement `verify` using a Lua script similar to `CaptchaStore`:

```java
private static final DefaultRedisScript<List> VERIFY_SCRIPT =
    new DefaultRedisScript<>("""
        local value = redis.call('GET', KEYS[1])
        if not value then
          return {'EXPIRED', 0}
        end
        local entry = cjson.decode(value)
        entry.attempts = tonumber(entry.attempts) + 1
        if entry.codeHash == ARGV[1] then
          redis.call('DEL', KEYS[1])
          return {'OK', entry.attempts}
        end
        if entry.attempts >= tonumber(ARGV[2]) then
          redis.call('DEL', KEYS[1])
          return {'LOCKED', entry.attempts}
        end
        local ttl = redis.call('TTL', KEYS[1])
        if ttl < 1 then ttl = 1 end
        redis.call('SET', KEYS[1], cjson.encode(entry), 'EX', ttl)
        return {'MISMATCH', entry.attempts}
        """, List.class);
```

- [ ] **Step 4: Run store tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EmailVerificationStoreTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/shuati/email/EmailVerificationStore.java backend/src/test/java/com/shuati/email/EmailVerificationStoreTest.java
git commit -m "feat(auth): 增加邮箱验证码Redis存储"
```

---

### Task 3: SMTP 发信适配器

**Files:**
- Create: `backend/src/main/java/com/shuati/email/EmailSender.java`
- Create: `backend/src/main/java/com/shuati/email/SmtpEmailSender.java`
- Create: `backend/src/test/java/com/shuati/email/SmtpEmailSenderTest.java`

**Interfaces:**
- Produces:
  - `void EmailSender.sendRegisterCode(String email, String code)`
- `SmtpEmailSender` consumes `JavaMailSender` and `EmailVerificationProperties`.

- [ ] **Step 1: Write failing sender test**

```java
@ActiveProfiles("test")
@SpringBootTest
class SmtpEmailSenderTest {

  @Autowired SmtpEmailSender sender;
  @MockitoBean JavaMailSender mailSender;

  @Test
  void sendsVerificationMailThroughConfiguredSender() {
    sender.sendRegisterCode("user@example.com", "123456");

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue()).isNotNull();
  }

}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=SmtpEmailSenderTest test
```

Expected: compilation FAIL because sender classes do not exist.

- [ ] **Step 3: Implement SMTP sender**

`SmtpEmailSender`:

```java
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {
  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;

  @Override
  public void sendRegisterCode(String email, String code) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(properties.from(), properties.fromName(), "UTF-8"));
      helper.setTo(email);
      helper.setSubject("【拾题】注册邮箱验证码");
      helper.setText(
          "您的注册验证码是：" + code + "\n\n验证码 5 分钟内有效。若非本人操作，请忽略本邮件。",
          "<p>您的注册验证码是：<strong>" + code + "</strong></p>"
              + "<p>验证码 5 分钟内有效。若非本人操作，请忽略本邮件。</p>");
      mailSender.send(message);
    } catch (Exception e) {
      throw new ApiException(503, "验证码邮件发送失败，请稍后重试");
    }
  }
}
```

- [ ] **Step 4: Run sender tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=SmtpEmailSenderTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/shuati/email/EmailSender.java backend/src/main/java/com/shuati/email/SmtpEmailSender.java backend/src/test/java/com/shuati/email/SmtpEmailSenderTest.java
git commit -m "feat(auth): 接入DirectMail SMTP发信"
```

---

### Task 4: 邮箱验证码服务与多维防刷

**Files:**
- Create: `backend/src/main/java/com/shuati/email/EmailVerificationService.java`
- Create: `backend/src/test/java/com/shuati/email/EmailVerificationServiceTest.java`

**Interfaces:**
- Consumes: `EmailVerificationStore`, `EmailSender`, `EmailVerificationProperties`, `RateLimiter`.
- Produces:
  - `SendResult sendRegisterCode(String email, String ip, String device)`
  - `void verifyRegisterCode(String email, String code)`

`SendResult` is:

```java
public record SendResult(int cooldownSeconds, int expiresInSeconds) {}
```

- [ ] **Step 1: Write failing service tests**

Test cases:

```java
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationServiceTest {

  @Autowired EmailVerificationService service;
  @Autowired EmailVerificationStore store;
  @Autowired StringRedisTemplate redis;
  @MockitoBean EmailSender sender;

  @BeforeEach
  void resetSender() {
    Mockito.reset(sender);
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
        .isInstanceOf(ApiException.class);
  }

  @Test
  void smtpFailureClearsCodeAndCooldownButKeepsCounters() {
    doThrow(new ApiException(503, "send failed"))
        .when(sender).sendRegisterCode(anyString(), anyString());
    assertThatThrownBy(() ->
        service.sendRegisterCode("three@example.com", "1.1.1.3", "device-3"))
        .isInstanceOf(ApiException.class);
    doNothing().when(sender).sendRegisterCode(anyString(), anyString());
    service.sendRegisterCode("three@example.com", "1.1.1.3", "device-3");
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EmailVerificationServiceTest test
```

Expected: compilation FAIL because `EmailVerificationService` does not exist.

- [ ] **Step 3: Implement send flow**

`sendRegisterCode` must:

1. Normalize email.
2. Check `enabled`.
3. Acquire cooldown with `store.acquireCooldown`.
4. Check limits through `RateLimiter.allow`:

```text
email:register:email:{emailHash}:{yyyyMMdd}
email:register:ip:{ip}:{yyyyMMddHH}
email:register:device:{deviceHash}:{yyyyMMdd}
email:register:global:{yyyyMMdd}
```

5. Generate `SecureRandom().nextInt(1_000_000)` formatted as `%06d`.
6. Save only `sha256(secret + ":" + email + ":" + code)`.
7. Call `EmailSender.sendRegisterCode`.
8. On sender failure, delete code and cooldown, rethrow 503.

Expose package-private deterministic helpers for internal reuse and tests:

```java
String emailHash(String email) {
  return sha256(properties.secret() + ":email:" + normalize(email));
}

String codeHash(String email, String code) {
  return sha256(properties.secret() + ":" + normalize(email) + ":" + code);
}
```

These methods are package-private so tests can seed a known code without
adding test-only behavior to the public service API.

- [ ] **Step 4: Implement verify flow**

`verifyRegisterCode`:

1. Hash submitted code using the same formula.
2. Call `store.verify`.
3. Throw `ApiException(400, "验证码错误或已过期")` unless `ok`.

- [ ] **Step 5: Run service and store tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EmailVerificationServiceTest,EmailVerificationStoreTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/shuati/email/EmailVerificationService.java backend/src/test/java/com/shuati/email/EmailVerificationServiceTest.java
git commit -m "feat(auth): 实现邮箱验证码防刷服务"
```

---

### Task 5: 发送接口与注册流程接入

**Files:**
- Create: `backend/src/main/java/com/shuati/email/EmailCodeRequest.java`
- Create: `backend/src/main/java/com/shuati/email/EmailVerificationController.java`
- Modify: `backend/src/main/java/com/shuati/auth/dto/RegisterRequest.java`
- Modify: `backend/src/main/java/com/shuati/auth/AuthController.java`
- Modify: `backend/src/main/java/com/shuati/auth/AuthService.java`
- Modify: `backend/src/test/java/com/shuati/auth/AuthFlowTest.java`
- Create: `backend/src/test/java/com/shuati/email/EmailVerificationControllerTest.java`

**Interfaces:**
- Consumes: `EmailVerificationService`, `CaptchaService`, `ProfileRepository`.
- Produces:
  - `POST /api/auth/register/email-code`
  - register request field `emailCode`

- [ ] **Step 1: Write failing controller test**

Test:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationControllerTest {

  @Autowired MockMvc mvc;
  @Autowired CaptchaService captchaService;
  @Autowired CaptchaStore captchaStore;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean EmailSender sender;

  @Test
  void sendCodeRequiresCaptchaAndReturnsCooldown() throws Exception {
    CaptchaImageResponse captcha = captchaService.generate("9.9.9.9", "device-x");
    String code = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();
    mvc.perform(post("/api/auth/register/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"new@example.com",
                 "captchaTicket":"%s",
                 "captchaCode":"%s"}
                """.formatted(captcha.ticket(), code)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cooldownSeconds").value(60));
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EmailVerificationControllerTest test
```

Expected: FAIL with 404 or compilation failure.

- [ ] **Step 3: Implement controller**

Create request record:

```java
public record EmailCodeRequest(
    @NotBlank @Email String email,
    @NotBlank String captchaTicket,
    @NotBlank String captchaCode) {}
```

Controller:

```java
@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
public class EmailVerificationController {
  private final CaptchaService captchaService;
  private final ProfileRepository profiles;
  private final EmailVerificationService emails;

  @PostMapping("/email-code")
  @RateLimit(name = "auth:email-code:ip", limit = 30,
      windowSeconds = 3600, scope = RateLimit.Scope.IP)
  public ApiResponse<EmailVerificationService.SendResult> send(
      @Valid @RequestBody EmailCodeRequest request,
      HttpServletRequest http) {
    captchaService.verify(request.captchaTicket(), request.captchaCode());
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (profiles.findByEmail(email).isPresent()) {
      throw new ApiException(400, "该邮箱已注册，请直接登录");
    }
    return ApiResponse.ok(emails.sendRegisterCode(
        email, ClientInfo.ip(http), ClientInfo.device(http)));
  }
}
```

- [ ] **Step 4: Change the register contract**

Replace `RegisterRequest` fields with:

```java
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, max = 64) String password,
    @NotBlank String emailCode) {}
```

In `AuthService.register`, replace captcha verification with:

```java
String email = normalizeEmail(request.email());
if (profiles.findByEmail(email).isPresent()) {
  throw new ApiException(400, "该邮箱已注册，请直接登录");
}
emailVerificationService.verifyRegisterCode(email, request.emailCode());
```

Set:

```java
profile.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
```

- [ ] **Step 5: Update AuthFlowTest**

Inject `EmailVerificationStore`, `EmailVerificationService`, and
`EmailVerificationProperties`, then add this helper:

```java
private String credentialsWithEmailCode(String email, String password) {
  String code = "123456";
  emailVerificationStore.saveCode(
      emailVerificationService.emailHash(email),
      emailVerificationService.codeHash(email, code),
      emailVerificationProperties.codeTtlSeconds());
  return """
      {"email":"%s","password":"%s","emailCode":"%s"}
      """.formatted(email, password, code);
}
```

Use that helper for the successful registration and duplicate-registration
setup. Add a test:

```java
@Test
void registerWithoutEmailCodeIsRejected() throws Exception {
  String email = "no-code-" + UUID.randomUUID() + "@example.com";
  mvc.perform(post("/api/auth/register")
          .header("X-Requested-With", "ShuatiApp")
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
              {"email":"%s","password":"secret123","emailCode":""}
              """.formatted(email)))
      .andExpect(status().isBadRequest());
}
```

- [ ] **Step 6: Run auth tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=AuthFlowTest,EmailVerificationControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/shuati/email/EmailCodeRequest.java backend/src/main/java/com/shuati/email/EmailVerificationController.java backend/src/main/java/com/shuati/auth/dto/RegisterRequest.java backend/src/main/java/com/shuati/auth/AuthController.java backend/src/main/java/com/shuati/auth/AuthService.java backend/src/test/java/com/shuati/auth/AuthFlowTest.java backend/src/test/java/com/shuati/email/EmailVerificationControllerTest.java
git commit -m "feat(auth): 注册前校验邮箱验证码"
```

---

### Task 6: 注册页发送验证码与 60 秒倒计时

**Files:**
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/tests/mobile-responsive.spec.ts`

**Interfaces:**
- Consumes: `POST /api/auth/register/email-code`, updated `POST /api/auth/register`.
- Produces: UI with email code input and countdown.

- [ ] **Step 1: Write failing Playwright test**

Add:

```ts
test("registration sends email code and starts sixty second countdown", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" } },
    }),
  );
  await page.route("**/api/auth/register/email-code", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { cooldownSeconds: 60, expiresInSeconds: 300 } },
    }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "注册" }).click();
  await page.getByLabel("邮箱").fill("user@example.com");
  await page.getByLabel("验证码").fill("AB3D");
  await page.getByRole("button", { name: "发送验证码" }).click();

  await expect(page.getByRole("button", { name: /60 秒后重发/ })).toBeDisabled();
  await expect(page.getByLabel("邮箱验证码")).toBeVisible();
});
```

Add a second test:

```ts
test("changing email clears the old code and countdown", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" } },
    }),
  );
  await page.route("**/api/auth/register/email-code", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { cooldownSeconds: 60, expiresInSeconds: 300 } },
    }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "注册" }).click();
  await page.getByLabel("邮箱").fill("first@example.com");
  await page.getByLabel("验证码").fill("AB3D");
  await page.getByRole("button", { name: "发送验证码" }).click();
  await page.getByLabel("邮箱").fill("second@example.com");

  await expect(page.getByLabel("邮箱验证码")).toHaveValue("");
  await expect(page.getByRole("button", { name: "发送验证码" })).toBeEnabled();
});
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
npm --prefix frontend run test:mobile
```

Expected: FAIL because the send button and email code input do not exist.

- [ ] **Step 3: Update auth store**

Add:

```ts
async register(
  email: string,
  password: string,
  emailCode: string,
) {
  this.user = await api<User>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password, emailCode }),
  });
  this.loaded = true;
}
```

Remove captcha parameters from register only.

- [ ] **Step 4: Update register UI**

Add state:

```ts
const emailCode = ref("");
const cooldown = ref(0);
let timer: number | undefined;

watch(email, () => {
  emailCode.value = "";
  cooldown.value = 0;
  if (timer) window.clearInterval(timer);
});
```

Add send function:

```ts
async function sendEmailCode() {
  error.value = "";
  const data = await api<{ cooldownSeconds: number; expiresInSeconds: number }>(
    "/api/auth/register/email-code",
    {
      method: "POST",
      body: JSON.stringify({
        email: email.value.trim(),
        captchaTicket: captchaTicket.value,
        captchaCode: captchaCode.value.trim(),
      }),
    },
  );
  emailCode.value = "";
  cooldown.value = data.cooldownSeconds;
  timer = window.setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0) window.clearInterval(timer);
  }, 1000);
  await loadCaptcha();
}
```

Render the email code field only in register mode:

```vue
<label v-if="mode === 'register'" class="mb-5 block">
  <span class="mb-1.5 block text-sm text-ink">邮箱验证码</span>
  <div class="flex items-center gap-3">
    <input
      v-model="emailCode"
      maxlength="6"
      inputmode="numeric"
      class="w-full flex-1 rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none focus:border-moss"
      placeholder="6 位数字"
    />
    <button
      type="button"
      :disabled="cooldown > 0"
      class="shrink-0 rounded-xl border border-moss px-4 py-2.5 text-sm text-moss disabled:opacity-50"
      @click="sendEmailCode"
    >
      {{ cooldown > 0 ? `${cooldown} 秒后重发` : "发送验证码" }}
    </button>
  </div>
</label>
```

Final register call:

```ts
await auth.register(email.value.trim(), password.value, emailCode.value.trim());
```

- [ ] **Step 5: Run frontend tests and build**

Run:

```bash
npm --prefix frontend run test:mobile
npm --prefix frontend run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/stores/auth.ts frontend/src/views/LoginView.vue frontend/tests/mobile-responsive.spec.ts
git commit -m "feat(auth): 注册页增加邮箱验证码倒计时"
```

---

### Task 7: 部署配置与端到端验证

**Files:**
- Modify: `deploy/env.example`
- Modify: `deploy/README.md`
- Modify: `deploy/scripts/provision-ubuntu.sh` only if it needs to preserve the new variables; do not overwrite `/etc/shuati/env`.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: deployable SMTP configuration and verification commands.

- [ ] **Step 1: Document the SMTP environment**

Add to `deploy/README.md`:

```text
SHUATI_MAIL_ENABLED=true
SHUATI_MAIL_HOST=smtpdm.aliyun.com
SHUATI_MAIL_PORT=465
SHUATI_MAIL_USERNAME=no-reply@mail.seaunit.site
SHUATI_MAIL_PASSWORD=在DirectMail控制台设置的SMTP密码
SHUATI_MAIL_FROM=no-reply@mail.seaunit.site
SHUATI_MAIL_FROM_NAME=拾题
SHUATI_EMAIL_SECRET=独立随机值
SHUATI_MAIL_SSL=true
```

- [ ] **Step 2: Verify the SMTP env is not committed**

Run:

```bash
git status --short
```

Expected: no `/etc/shuati/env`, `.env`, or SMTP password appears in Git output.

- [ ] **Step 3: Verify Cloudflare real IP is used by Nginx**

Run on the server:

```bash
grep -q 'CF-Connecting-IP\|cf_connecting_ip' /etc/nginx/conf.d/shuati.conf
```

Expected: exit code `0`. If it fails, run:

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/enable-cloudflare-proxy.sh | sudo bash
```

- [ ] **Step 4: Run the whole backend suite**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: PASS.

- [ ] **Step 5: Run frontend tests and build**

Run:

```bash
npm --prefix frontend run test:mobile
npm --prefix frontend run build
```

Expected: PASS.

- [ ] **Step 6: Run one real registration verification**

After deploying to `seaunit.site`:

1. Open `https://seaunit.site/login`
2. Switch to register
3. Enter a real QQ mailbox
4. Complete image captcha
5. Click `发送验证码`
6. Confirm the mail arrives within 60 seconds
7. Enter the 6-digit code and finish registration
8. Confirm `profiles.email_verified_at` is non-null

Expected:

```text
2026-10-09 ... email_verified_at is not null
```

- [ ] **Step 7: Commit deployment docs**

```bash
git add deploy/env.example deploy/README.md
git commit -m "docs(auth): 补充注册邮箱验证码部署配置"
```
