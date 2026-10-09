# 找回密码设计

## 目标

使用现有阿里云 DirectMail 邮箱服务实现找回密码。用户通过邮箱验证码证明账号控制权后，
设置新密码，并使所有已签发的旧 JWT 立即失效。

## 范围

包含：

- 登录页找回密码入口；
- 发送重置密码邮箱验证码；
- 校验验证码并重置密码；
- 注册验证码与重置验证码用途隔离；
- 重置后全端登录态失效；
- 邮箱不存在时不泄露账号状态；
- 复用现有 DirectMail SMTP、Redis、限流、图形验证码和审计边界。

不包含：

- 短信找回；
- 修改邮箱；
- 管理员代重置密码流程；
- 登录设备和会话列表管理；
- 多因素认证。

## 总体流程

```text
登录页点击“忘记密码”
  -> 输入邮箱和图形验证码
  -> POST /api/auth/password-reset/email-code
  -> 服务端执行冷却与多维限额
  -> 若邮箱存在，生成 RESET 用途验证码并通过 DirectMail 发送
  -> 若邮箱不存在，返回与存在时相同的响应，不发送邮件
  -> 用户输入邮箱验证码、新密码、确认密码
  -> POST /api/auth/password-reset
  -> Redis 原子校验并删除 RESET 验证码
  -> 更新 password_hash、email_verified_at
  -> session_version 加一
  -> 所有旧 JWT 因版本不匹配失效
  -> 前端跳转登录页
```

## 接口

### 发送重置验证码

```http
POST /api/auth/password-reset/email-code
```

请求：

```json
{
  "email": "user@example.com",
  "captchaTicket": "ticket",
  "captchaCode": "AB3D"
}
```

成功响应：

```json
{
  "cooldownSeconds": 60,
  "expiresInSeconds": 300
}
```

行为要求：

- 通过现有 `CaptchaService` 校验并一次性消费图形验证码；
- 对存在和不存在的邮箱返回相同结构；
- 不存在的邮箱不发送邮件，但仍执行冷却、邮箱/IP/设备/全站限额；
- SMTP 不可用时返回 503；
- 响应和日志不得包含验证码明文。

### 重置密码

```http
POST /api/auth/password-reset
```

请求：

```json
{
  "email": "user@example.com",
  "emailCode": "123456",
  "newPassword": "new-password"
}
```

行为要求：

1. 校验邮箱格式和新密码长度 6–64；
2. 查询账号；
3. 校验 RESET 用途邮箱验证码；
4. 成功后立即删除验证码；
5. 更新密码 BCrypt 哈希；
6. 写入当前 UTC `email_verified_at`；
7. `session_version` 加一；
8. 返回成功。

密码重置不要求额外图形验证码，因为 RESET 邮箱验证码已经证明邮箱控制权。

## 验证码用途隔离

`EmailVerificationService` 改为使用显式用途：

```java
public enum EmailPurpose {
  REGISTER,
  RESET
}
```

Redis key：

```text
email:register:code:{emailHash}
email:register:cooldown:{emailHash}

email:reset:code:{emailHash}
email:reset:cooldown:{emailHash}
```

验证码哈希：

```text
sha256(secret + ":" + purpose + ":" + normalizedEmail + ":" + code)
```

REGISTER 和 RESET 的验证码、冷却和尝试次数完全隔离，不能跨场景复用。

邮件主题和正文按用途变化：

```text
注册： 【拾题】注册邮箱验证码
重置： 【拾题】重置密码验证码
```

SMTP 适配器改为：

```java
void sendCode(String email, String code, EmailPurpose purpose);
```

## 会话版本

`profiles` 新增：

```sql
ALTER TABLE profiles
  ADD COLUMN session_version int NOT NULL DEFAULT 0 AFTER email_verified_at;
```

`Profile` 实体增加 `sessionVersion`。

JWT 增加 claim：

```text
sv = profiles.session_version
```

JWT payload：

```java
record JwtPayload(String userId, String role, int sessionVersion) {}
```

认证过滤器按以下规则校验：

- 查询当前用户 `session_version`；
- Token 没有 `sv` 时按 `0` 处理；
- Token `sv` 与数据库版本不一致时认证失败，返回 401；
- 用户不存在或禁用时认证失败；
- 原有 JWT 签名和过期时间校验继续生效。

注册、登录和密码重置操作需要正确签发或递增版本：

```text
注册 -> session_version = 0，JWT sv = 0
登录 -> JWT sv = 当前 session_version
重置 -> session_version += 1，旧 Token 全部失效
```

## 防刷

发送重置验证码沿用注册验证码的限制：

| 维度 | 默认限制 |
| --- | ---: |
| 同邮箱冷却 | 60 秒 |
| 同邮箱每日 | 10 次 |
| 同 IP 每小时 | 20 次 |
| 同设备每日 | 30 次 |
| 全站每日 | 2000 次 |
| 单验证码最大错误 | 5 次 |
| 验证码有效期 | 300 秒 |

429 响应继续包含剩余秒数并设置 `Retry-After`。

## 错误处理

- 不存在邮箱：发送接口仍返回成功响应，不泄露账号是否存在；
- 验证码错误或过期：统一返回“验证码错误或已过期”；
- SMTP 失败：删除当前 RESET 验证码和冷却，不回滚已消耗限额；
- Redis 不可用：找回密码 fail-closed，不允许重置；
- 新密码格式错误：400；
- 重置成功后旧 JWT：401。

## 前端

登录页增加 `忘记密码` 模式或入口，使用与注册相同的邮箱验证码倒计时组件逻辑：

- 邮箱输入；
- 新密码和确认密码；
- 图形验证码；
- 邮箱验证码；
- `发送验证码` 按钮；
- 60 秒倒计时；
- 修改邮箱时清空旧验证码和倒计时；
- 重置成功后提示并切换回登录模式。

前端不能判断邮箱是否存在，也不能保存验证码明文。

## 测试

后端覆盖：

- REGISTER 验证码不能用于 RESET；
- RESET 验证码不能用于 REGISTER；
- 不存在邮箱发送接口响应与存在邮箱一致；
- 不存在邮箱不调用 `EmailSender`；
- 60 秒冷却、邮箱/IP/设备/全站限额；
- 验证码错误 5 次后失效；
- 验证码成功后不可重复使用；
- 新密码登录成功、旧密码登录失败；
- 重置后旧 JWT 返回 401；
- 重置后新登录 JWT 可正常使用；
- SMTP 失败清理当前 RESET 验证码；
- `session_version` 迁移和管理员引导默认值。

前端覆盖：

- 忘记密码入口；
- 发送验证码倒计时；
- 修改邮箱清理旧状态；
- 未输入新密码或验证码不能提交；
- 重置成功跳回登录页。

## 发布顺序

1. 部署 Flyway V10；
2. 部署后端和前端；
3. 使用真实 QQ 邮箱完成一次找回密码；
4. 验证旧密码、旧 JWT 和新密码行为；
5. 检查 DirectMail 发送统计。
