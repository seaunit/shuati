# 找回密码 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用现有 DirectMail 邮箱验证码实现找回密码，并在重置后让全部旧 JWT 失效。

**Architecture:** 复用现有 Redis 邮箱验证码基础设施，通过 `EmailPurpose` 隔离注册和重置场景；在 `profiles` 增加 `session_version`，JWT 携带版本并在认证过滤器中校验；新增找回密码服务和接口。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Mail, MySQL/Flyway, Redis, Vue 3, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-09-password-reset-design.md`

## Global Constraints

- REGISTER 和 RESET 的 Redis key、哈希、冷却和尝试次数完全隔离。
- 发送重置验证码对存在和不存在的邮箱返回相同结构。
- 不存在的邮箱不调用 `EmailSender`，但仍消耗冷却和邮箱/IP/设备/全站限额。
- 重置验证码 300 秒有效，最多错误 5 次，成功后立即删除。
- 重置成功后更新 `password_hash`、`email_verified_at`，并让 `session_version += 1`。
- 旧 JWT 必须立即返回 401；缺少 `sv` 的旧 Token 按版本 0 处理。
- 429 响应必须包含剩余等待秒数和 `Retry-After`。
- SMTP 失败回滚当前 RESET 验证码和冷却，不回滚已消耗限额。

## Review Focus

- REGISTER 验证码不能用于 RESET，RESET 验证码不能用于 REGISTER。
- 不存在邮箱不能通过响应差异或调用次数泄露账号状态。
- 并发重置请求只能有一个成功消费 RESET 验证码。
- 重置成功后，旧密码不能登录、旧 JWT 不能访问 `/api/me`、新密码可登录。
- SMTP 延时失败不能删除后续验证码；Redis 不可用必须 fail-closed。

---

### Task 1: 邮箱验证码用途隔离

**Files:**
- Create: `backend/src/main/java/com/shuati/email/EmailPurpose.java`
- Modify: `backend/src/main/java/com/shuati/email/EmailVerificationStore.java`
- Modify: `backend/src/main/java/com/shuati/email/EmailVerificationService.java`
- Modify: `backend/src/main/java/com/shuati/email/EmailSender.java`
- Modify: `backend/src/main/java/com/shuati/email/SmtpEmailSender.java`
- Modify: `backend/src/main/java/com/shuati/email/EmailVerificationController.java`
- Modify: `backend/src/main/java/com/shuati/auth/AuthService.java`
- Modify: `backend/src/test/java/com/shuati/email/EmailVerificationStoreTest.java`
- Modify: `backend/src/test/java/com/shuati/email/EmailVerificationServiceTest.java`
- Modify: `backend/src/test/java/com/shuati/email/SmtpEmailSenderTest.java`
- Modify: `backend/src/test/java/com/shuati/email/EmailVerificationTestSupport.java`

**Interfaces:**
- Produces:
  - `enum EmailPurpose { REGISTER, RESET }`
  - `void EmailSender.sendCode(String email, String code, EmailPurpose purpose)`
  - `SendResult EmailVerificationService.sendCode(String email, String ip, String device, EmailPurpose purpose)`
  - `SendResult EmailVerificationService.consumeAllowance(String email, String ip, String device, EmailPurpose purpose)`
  - `void EmailVerificationService.verifyCode(String email, String code, EmailPurpose purpose)`

- [ ] **Step 1: Write purpose isolation tests**

Add to `EmailVerificationServiceTest`:

```java
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
```

Add store key test:

```java
@Test
void purposeKeysAreIndependent() {
  String emailHash = "purpose-" + UUID.randomUUID();
  store.saveCode(emailHash, EmailPurpose.REGISTER, "register-hash", 300);
  store.saveCode(emailHash, EmailPurpose.RESET, "reset-hash", 300);

  assertThat(store.verify(
      emailHash, EmailPurpose.REGISTER, "register-hash", 5).ok()).isTrue();
  assertThat(store.verify(
      emailHash, EmailPurpose.RESET, "reset-hash", 5).ok()).isTrue();
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml "-Dtest=EmailVerificationStoreTest,EmailVerificationServiceTest" test
```

Expected: compilation FAIL because `EmailPurpose` and new signatures do not exist.

- [ ] **Step 3: Implement purpose-aware store**

Create:

```java
public enum EmailPurpose {
  REGISTER,
  RESET
}
```

Change store keys to:

```java
private String prefix(EmailPurpose purpose) {
  return "email:" + purpose.name().toLowerCase(Locale.ROOT) + ":";
}
```

Every store method accepts `EmailPurpose purpose` and prefixes both code and cooldown keys.

- [ ] **Step 4: Implement purpose-aware service and sender**

Hash input:

```java
properties.secret() + ":" + purpose.name() + ":" + normalizedEmail + ":" + code
```

Rate-limit keys:

```text
email:{purpose}:email:{emailHash}:{yyyyMMdd}
email:{purpose}:ip:{ip}:{yyyyMMddHH}
email:{purpose}:device:{deviceHash}:{yyyyMMdd}
email:{purpose}:global:{yyyyMMdd}
```

SMTP subject:

```java
purpose == EmailPurpose.REGISTER
    ? "【拾题】注册邮箱验证码"
    : "【拾题】重置密码验证码"
```

Update existing callers to use `EmailPurpose.REGISTER`.

Update the test helper signature:

```java
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
```

- [ ] **Step 5: Run purpose tests**

Run:

```bash
mvn -f backend/pom.xml "-Dtest=EmailVerificationStoreTest,EmailVerificationServiceTest,SmtpEmailSenderTest" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/shuati/email backend/src/main/java/com/shuati/auth/AuthService.java backend/src/test/java/com/shuati/email
git commit -m "refactor(auth): 隔离注册与重置邮箱验证码"
```

---

### Task 2: JWT 会话版本与全端失效

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__session_version.sql`
- Modify: `backend/src/main/java/com/shuati/user/Profile.java`
- Modify: `backend/src/main/java/com/shuati/config/AdminBootstrap.java`
- Modify: `backend/src/main/java/com/shuati/auth/JwtService.java`
- Modify: `backend/src/main/java/com/shuati/auth/JwtAuthFilter.java`
- Modify: `backend/src/main/java/com/shuati/auth/AuthService.java`
- Modify: `backend/src/test/java/com/shuati/auth/AuthFlowTest.java`
- Create: `backend/src/test/java/com/shuati/auth/JwtSessionVersionTest.java`

**Interfaces:**
- Produces:
  - `String JwtService.issue(String userId, String role, int sessionVersion)`
  - `record JwtPayload(String userId, String role, int sessionVersion)`

- [ ] **Step 1: Write failing session-version test**

Create `JwtSessionVersionTest`:

```java
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class JwtSessionVersionTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtService jwtService;

  @Test
  void tokenWithStaleSessionVersionIsRejected() throws Exception {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, 'x', 'USER', 'ENABLED', 1)
        """, userId, "session-" + userId + "@example.com");

    String stale = jwtService.issue(userId, "USER", 0);
    mvc.perform(get("/api/me").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, stale)))
        .andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=JwtSessionVersionTest test
```

Expected: FAIL because `session_version` column and method overload do not exist.

- [ ] **Step 3: Add migration and entity field**

Migration:

```sql
ALTER TABLE profiles
  ADD COLUMN session_version int NOT NULL DEFAULT 0 AFTER email_verified_at;
```

Entity:

```java
@Column(name = "session_version", nullable = false)
private int sessionVersion = 0;
```

AdminBootstrap relies on the default `0`.

- [ ] **Step 4: Include session version in JWT and validate it**

JWT:

```java
.claim("sv", sessionVersion)
```

Filter:

```java
Optional<Profile> profileOpt = profiles.findById(payload.userId());
if (profileOpt.isPresent()) {
  Profile profile = profileOpt.get();
  boolean enabled = !"DISABLED".equals(profile.getStatus());
  boolean versionMatches = payload.sessionVersion() == profile.getSessionVersion();
  if (enabled && versionMatches) {
    String role = payload.role() == null ? profile.getRole() : payload.role();
    var authentication = new UsernamePasswordAuthenticationToken(
        payload.userId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    authentication.setDetails(
        new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
```

Missing `sv` maps to `0`:

```java
Integer sv = claims.get("sv", Integer.class);
int sessionVersion = sv == null ? 0 : sv;
```

- [ ] **Step 5: Update all JWT issuance**

Register and login:

```java
jwtService.issue(profile.getId(), profile.getRole(), profile.getSessionVersion())
```

Update all existing test calls to the three-argument overload.

- [ ] **Step 6: Run auth tests**

Run:

```bash
mvn -f backend/pom.xml "-Dtest=AuthFlowTest,JwtSessionVersionTest" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__session_version.sql backend/src/main/java/com/shuati/user/Profile.java backend/src/main/java/com/shuati/config/AdminBootstrap.java backend/src/main/java/com/shuati/auth backend/src/test/java/com/shuati/auth
git commit -m "feat(auth): 增加JWT会话版本校验"
```

---

### Task 3: 找回密码服务与接口

**Files:**
- Create: `backend/src/main/java/com/shuati/password/PasswordResetController.java`
- Create: `backend/src/main/java/com/shuati/password/PasswordResetService.java`
- Create: `backend/src/main/java/com/shuati/password/PasswordResetCodeRequest.java`
- Create: `backend/src/main/java/com/shuati/password/PasswordResetRequest.java`
- Create: `backend/src/test/java/com/shuati/password/PasswordResetFlowTest.java`
- Modify: `backend/src/main/java/com/shuati/email/EmailVerificationService.java`
- Modify: `backend/src/main/java/com/shuati/auth/AuthService.java` only if it exposes a reusable profile lookup helper

**Interfaces:**
- Produces:
  - `SendResult PasswordResetService.sendCode(String email, String ip, String device)`
  - `void PasswordResetService.reset(String email, String emailCode, String newPassword)`

- [ ] **Step 1: Write failing password-reset tests**

Create `PasswordResetFlowTest`:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetFlowTest {

  @Autowired MockMvc mvc;
  @Autowired CaptchaService captchaService;
  @Autowired ObjectMapper objectMapper;
  @Autowired CaptchaStore captchaStore;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired EmailVerificationStore emailStore;
  @Autowired EmailVerificationService emailService;
  @Autowired JwtService jwtService;
  @MockitoBean EmailSender sender;

  @Test
  void existingAndMissingEmailReturnSameSendResult() throws Exception {
    String existing = insertUser("reset-existing@example.com", "old-password");
    String missing = "reset-missing@example.com";

    expectCodeSent(existing, "9.8.7.1", "reset-device-1", true);
    expectCodeSent(missing, "9.8.7.2", "reset-device-2", false);
  }

  @Test
  void resetChangesPasswordAndRejectsOldJwt() throws Exception {
    String email = "reset-jwt@example.com";
    String userId = insertUser(email, "old-password");
    String oldJwt = jwtService.issue(userId, "USER", 0);
    seedResetCode(email, "123456");

    mvc.perform(post("/api/auth/password-reset")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","emailCode":"123456","newPassword":"new-password"}
                """.formatted(email)))
        .andExpect(status().isOk());

    mvc.perform(get("/api/me")
            .cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, oldJwt)))
        .andExpect(status().isUnauthorized());

    Map<String, Object> profile = jdbc.queryForMap(
        "select password_hash, session_version from profiles where id = ?", userId);
    assertThat(passwordEncoder.matches("new-password",
        String.valueOf(profile.get("password_hash")))).isTrue();
    assertThat(passwordEncoder.matches("old-password",
        String.valueOf(profile.get("password_hash")))).isFalse();
    assertThat(((Number) profile.get("session_version")).intValue()).isEqualTo(1);
  }

  @Test
  void resetCodeCannotBeReused() throws Exception {
    String email = "reset-reuse@example.com";
    insertUser(email, "old-password");
    seedResetCode(email, "123456");

    reset(email, "123456", "first-new-password").andExpect(status().isOk());
    reset(email, "123456", "second-new-password")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
  }

  private void expectCodeSent(
      String email, String ip, String device, boolean senderCalled) throws Exception {
    CaptchaImageResponse captcha = captchaService.generate(ip, device);
    String captchaCode = objectMapper.readTree(captchaStore.rawEntry(captcha.ticket()))
        .path("code").asText();
    mvc.perform(post("/api/auth/password-reset/email-code")
            .header("X-Requested-With", "ShuatiApp")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","captchaTicket":"%s","captchaCode":"%s"}
                """.formatted(email, captcha.ticket(), captchaCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
        .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    if (senderCalled) {
      verify(sender).sendCode(eq(email), anyString(), eq(EmailPurpose.RESET));
    } else {
      verify(sender, never()).sendCode(eq(email), anyString(), eq(EmailPurpose.RESET));
    }
  }

  private ResultActions reset(String email, String code, String password) throws Exception {
    return mvc.perform(post("/api/auth/password-reset")
        .header("X-Requested-With", "ShuatiApp")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email":"%s","emailCode":"%s","newPassword":"%s"}
            """.formatted(email, code, password)));
  }

  private String insertUser(String email, String password) {
    String userId = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles
          (id, email, password_hash, role, status, session_version)
        values (?, ?, ?, 'USER', 'ENABLED', 0)
        """, userId, email, passwordEncoder.encode(password));
    return userId;
  }

  private void seedResetCode(String email, String code) {
    EmailVerificationTestSupport.seed(
        emailStore, emailService,
        new EmailVerificationProperties(
            true, "no-reply@example.com", "拾题", "test-email-secret",
            300, 60, 5, 10, 20, 30, 2000),
        email, code, EmailPurpose.RESET);
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
mvn -f backend/pom.xml -Dtest=PasswordResetFlowTest test
```

Expected: FAIL with 404 or missing classes.

- [ ] **Step 3: Implement reset send flow**

Controller:

```java
@PostMapping("/email-code")
@RateLimit(name = "auth:password-reset-code:ip", limit = 30,
    windowSeconds = 3600, scope = RateLimit.Scope.IP)
public ApiResponse<EmailVerificationService.SendResult> sendCode(...) {
  captchaService.verify(request.captchaTicket(), request.captchaCode());
  return ApiResponse.ok(passwordResetService.sendCode(
      request.email(), ClientInfo.ip(http), ClientInfo.device(http)));
}
```

Service:

```java
Profile profile = profiles.findByEmail(normalized).orElse(null);
if (profile == null) {
  return emailVerificationService.consumeAllowance(
      normalized, ip, device, EmailPurpose.RESET);
}
return emailVerificationService.sendCode(
    normalized, ip, device, EmailPurpose.RESET);
```

Add `consumeAllowance` to `EmailVerificationService`: it acquires cooldown, checks all
limits, does not generate or store a code, and returns the same `SendResult`.

- [ ] **Step 4: Implement password reset transaction**

```java
@Transactional
public void reset(String email, String emailCode, String newPassword) {
  String normalized = normalize(email);
  Profile profile = profiles.findByEmail(normalized)
      .orElseThrow(() -> new ApiException(400, "验证码错误或已过期"));
  emailVerificationService.verifyCode(
      normalized, emailCode, EmailPurpose.RESET);
  profile.setPasswordHash(passwordEncoder.encode(newPassword));
  profile.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
  profile.setSessionVersion(profile.getSessionVersion() + 1);
  profiles.save(profile);
}
```

- [ ] **Step 5: Run password-reset tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=PasswordResetFlowTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/shuati/password backend/src/main/java/com/shuati/email/EmailVerificationService.java backend/src/test/java/com/shuati/password
git commit -m "feat(auth): 实现邮箱找回密码"
```

---

### Task 4: 前端忘记密码流程

**Files:**
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/tests/mobile-responsive.spec.ts`

**Interfaces:**
- Consumes:
  - `POST /api/auth/password-reset/email-code`
  - `POST /api/auth/password-reset`
- Produces: forgot-password UI mode with email code and countdown.

- [ ] **Step 1: Write failing frontend tests**

Add:

```ts
test("forgot password sends reset code and edits password", async ({ page }) => {
  await page.route("**/captcha/image", ...);
  await page.route("**/api/auth/password-reset/email-code", ...);
  await page.route("**/api/auth/password-reset", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: null } }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "忘记密码" }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("reset@example.com");
  await page.getByPlaceholder("请输入图中字符").fill("AB3D");
  await page.getByRole("button", { name: "发送验证码" }).click();
  await page.getByPlaceholder("6 位数字").fill("123456");
  await page.getByLabel("新密码").fill("new-secret");
  await page.getByLabel("确认新密码").fill("new-secret");
  await page.getByRole("button", { name: "重置密码" }).click();

  await expect(page).toHaveURL(/\/login/);
});
```

- [ ] **Step 2: Run to verify failure**

Run:

```bash
npm --prefix frontend run test:mobile
```

Expected: FAIL because forgotten-password UI does not exist.

- [ ] **Step 3: Add reset API to auth store**

```ts
async resetPassword(email: string, emailCode: string, newPassword: string) {
  await api<null>("/api/auth/password-reset", {
    method: "POST",
    body: JSON.stringify({ email, emailCode, newPassword }),
  });
}
```

- [ ] **Step 4: Add forgot-password mode**

Mode union:

```ts
const mode = ref<"login" | "register" | "forgot">("login");
```

Send endpoint:

```ts
const path = mode.value === "forgot"
  ? "/api/auth/password-reset/email-code"
  : "/api/auth/register/email-code";
```

Forgot mode fields:

- 邮箱
- 邮箱验证码
- 新密码
- 确认新密码
- 图形验证码

After successful reset:

```ts
mode.value = "login";
password.value = "";
confirm.value = "";
emailCode.value = "";
error.value = "";
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
git commit -m "feat(auth): 增加前端找回密码流程"
```

---

### Task 5: 完整回归与部署验证

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/superpowers/specs/2026-10-09-password-reset-design.md` only if implementation reveals a documented mismatch

**Interfaces:**
- Consumes all previous tasks.
- Produces verified deployable password reset.

- [ ] **Step 1: Run complete backend suite**

```bash
mvn -f backend/pom.xml test
```

Expected: PASS.

- [ ] **Step 2: Run frontend tests and build**

```bash
npm --prefix frontend run test:mobile
npm --prefix frontend run build
```

Expected: PASS.

- [ ] **Step 3: Verify migration on a clean test database**

Run Flyway through the test profile, then query:

```sql
select column_name, column_default
  from information_schema.columns
 where table_schema = 'shuati_test'
   and table_name = 'profiles'
   and column_name = 'session_version';
```

Expected: one row, default `0`.

- [ ] **Step 4: Run real email reset**

On `https://seaunit.site`:

1. Open login page.
2. Click `忘记密码`.
3. Enter a real QQ mailbox.
4. Complete parsing captcha and send reset code.
5. Enter code and new password.
6. Confirm the old password fails.
7. Confirm the new password succeeds.
8. Confirm the old JWT is rejected.

- [ ] **Step 5: Update deployment notes**

Document:

```text
Flyway V10 adds profiles.session_version.
Reset password increments session_version and invalidates all old JWT tokens.
No new SMTP environment variables are required.
```

- [ ] **Step 6: Commit**

```bash
git add deploy/README.md docs/superpowers/specs/2026-10-09-password-reset-design.md
git commit -m "docs(auth): 补充找回密码部署验证"
```
