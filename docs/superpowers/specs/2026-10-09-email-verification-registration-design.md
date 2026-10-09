# 注册邮箱验证码设计

## 目标

注册时必须证明用户能控制所填邮箱。只有邮箱验证码校验成功后，才允许创建账号、
初始化点数账户并登录。

本方案需要同时满足：

- 国内 QQ、163 等邮箱可稳定收到验证码；
- 同一邮箱 60 秒内只能发送一次；
- 防止单邮箱、单 IP、单设备和全站维度的邮件轰炸；
- 验证码只在服务端保存，前端永远拿不到明文答案；
- 验证码一次性使用，成功后立即失效；
- 阿里云 DirectMail 故障时不允许产生未验证账号。

## 非目标

- 本期不实现找回密码、修改邮箱和登录二次验证；
- 不迁移或强制验证已有账号；
- 不替代现有图形验证码；
- 不接入短信验证码。

## 服务选型

邮件服务使用阿里云邮件推送 DirectMail：

```text
SMTP 服务器：smtpdm.aliyun.com
端口：465
加密：SSL
发信地址：no-reply@mail.seaunit.site
发信类型：触发邮件
```

发信域名已完成以下 DNS 校验：

```text
mail.seaunit.site TXT  SPF
mail.seaunit.site MX   mx01.dm.aliyun.com
DKIM TXT
DMARC TXT
```

Spring Boot 使用 `spring-boot-starter-mail`，不为 DirectMail 单独引入云厂商 SDK。

## 总体流程

```text
注册页
  -> 用户填写邮箱并完成图形验证码
  -> POST /api/auth/register/email-code
  -> Redis 校验冷却与多维度限额
  -> 生成 6 位验证码并哈希后写入 Redis
  -> 通过 DirectMail SMTP 发送邮件
  -> 前端开始 60 秒倒计时
  -> 用户输入邮箱验证码、密码和确认密码
  -> POST /api/auth/register
  -> Redis 原子校验并删除验证码
  -> 创建 profiles、point_account
  -> 写入 email_verified_at
  -> 发放登录 Cookie
```

## 后端组件

新增以下组件：

```text
EmailVerificationController
EmailVerificationService
EmailVerificationStore
EmailSender
SmtpEmailSender
EmailProperties
```

职责边界：

- `EmailVerificationController`：只负责 HTTP 入参和响应；
- `EmailVerificationService`：验证码生成、冷却、限额、校验和一次性消费；
- `EmailVerificationStore`：Redis key、Lua 原子操作和 TTL；
- `EmailSender`：发信接口，便于测试替换；
- `SmtpEmailSender`：阿里云 DirectMail SMTP 实现；
- `EmailProperties`：邮件开关、SMTP 参数、验证码 TTL 和限额配置。

## 接口

### 发送注册邮箱验证码

```http
POST /api/auth/register/email-code
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

安全要求：

- 先校验图形验证码，通过后图形验证码立即作废；
- 邮箱不存在时才发送邮件；
- 已注册邮箱在校验图形验证码后返回“该邮箱已注册”；
- 响应和日志中绝不包含邮箱验证码明文；
- SMTP 发送失败时删除刚写入的验证码和 cooldown，允许用户在重新完成图形验证码后重试。
- SMTP 失败时已消耗的邮箱、IP、设备和全站限额不回滚，防止利用发信失败持续消耗服务商；
- 用户输入只作为收件地址使用，不进入邮件主题、发信人或自定义邮件头，避免邮件头注入。

### 注册

```http
POST /api/auth/register
```

请求新增：

```json
{
  "email": "user@example.com",
  "password": "password",
  "emailCode": "123456"
}
```

注册流程：

1. 校验邮箱格式和密码长度；
2. 检查邮箱是否已注册；
3. 从 Redis 校验邮箱验证码；
4. 校验成功后立即删除验证码；
5. 创建用户和点数账户；
6. 写入 `email_verified_at`；
7. 返回登录 Cookie。

注册接口不再重复要求图形验证码，因为邮箱验证码已经证明邮箱控制权。

## Redis 设计

所有 key 使用哈希后的邮箱，避免把完整邮箱写入 Redis key：

```text
email:register:code:{emailHash}
email:register:cooldown:{emailHash}
email:register:email-count:{emailHash}:{yyyyMMdd}
email:register:ip-count:{ip}:{yyyyMMddHH}
email:register:device-count:{deviceHash}:{yyyyMMdd}
email:register:global-count:{yyyyMMdd}
```

验证码条目结构：

```json
{
  "codeHash": "sha256(secret:email:code)",
  "attempts": 0
}
```

建议 TTL：

| key | TTL |
| --- | ---: |
| 验证码 | 5 分钟 |
| 同邮箱冷却 | 60 秒 |
| 邮箱日计数 | 24 小时 |
| IP 小时计数 | 1 小时 |
| 设备日计数 | 24 小时 |
| 全站日计数 | 24 小时 |

验证码校验使用 Redis Lua 保证原子性：

- codeHash 不匹配时 attempts + 1；
- attempts 达到 5 立即删除并失效；
- codeHash 匹配时立即删除并返回成功。

## 防刷与限额

发送邮箱验证码必须同时通过以下限制：

| 维度 | 默认限制 | 目的 |
| --- | ---: | --- |
| 同邮箱冷却 | 60 秒 | 满足产品要求 |
| 同邮箱每日 | 10 次 | 防止轰炸单个邮箱 |
| 同 IP 每小时 | 20 次 | 防止批量脚本 |
| 同设备每日 | 30 次 | 防止代理 IP 轮换 |
| 全站每日 | 2000 次 | 防止成本失控 |
| 同一验证码错误 | 最多 5 次 | 防止暴力猜测 |

除 Redis 业务限制外：

- 发送接口增加 `@RateLimit`，按 IP 限制；
- 现有全局 IP 限流继续生效；
- Nginx 边缘限流继续生效；
- 发送验证码前必须通过图形验证码。

所有限额写入配置，不在代码中硬编码。

## 数据库迁移

`profiles` 新增：

```sql
ALTER TABLE profiles
  ADD COLUMN email_verified_at datetime(6) NULL AFTER status;
```

规则：

- 已有用户保持 `NULL`，不做强制补验证；
- 新注册用户成功后写入当前 UTC 时间；
- 管理员引导创建的管理员默认视为已验证，写入当前时间。

## 邮件内容

主题：

```text
【拾题】注册邮箱验证码
```

正文：

```text
您的注册验证码是：123456

验证码 5 分钟内有效。若非本人操作，请忽略本邮件。
```

邮件同时提供纯文本和 HTML 版本，不包含推广内容，降低被判定为营销邮件的概率。

## 配置

新增环境变量：

```text
SHUATI_MAIL_ENABLED=true
SHUATI_MAIL_HOST=smtpdm.aliyun.com
SHUATI_MAIL_PORT=465
SHUATI_MAIL_USERNAME=no-reply@mail.seaunit.site
SHUATI_MAIL_PASSWORD=change-me
SHUATI_MAIL_FROM=no-reply@mail.seaunit.site
SHUATI_MAIL_FROM_NAME=拾题
SHUATI_MAIL_SSL=true
SHUATI_MAIL_CONNECT_TIMEOUT_MS=10000
SHUATI_MAIL_READ_TIMEOUT_MS=10000
SHUATI_MAIL_CODE_TTL_SECONDS=300
SHUATI_MAIL_COOLDOWN_SECONDS=60
SHUATI_MAIL_EMAIL_DAILY_LIMIT=10
SHUATI_MAIL_IP_HOURLY_LIMIT=20
SHUATI_MAIL_DEVICE_DAILY_LIMIT=30
SHUATI_MAIL_GLOBAL_DAILY_LIMIT=2000
```

SMTP 密码只写入 `/etc/shuati/env`，不进入 Git。

## 前端改造

注册页新增：

- 邮箱验证码输入框；
- `发送验证码` 按钮；
- 60 秒倒计时；
- 邮箱验证码发送错误提示；
- 图形验证码在发送邮箱验证码前使用；
- 注册按钮在邮箱验证码为空时不可提交；
- 用户修改邮箱后清空旧邮箱验证码并重置前端倒计时，旧验证码不能用于新邮箱注册。

前端不自行判断验证码是否正确，不做邮箱真实性判断。

## 错误处理

- SMTP 关闭或不可用：返回 503，不创建用户；
- SMTP 超时：删除验证码和 cooldown，提示稍后重试；
- 邮箱已注册：保留当前明确提示；
- 验证码错误或过期：统一返回“验证码错误或已过期”；
- 超过发送限额：返回 429 和剩余等待时间；
- Redis 不可用：验证码功能 fail-closed，不允许注册。

## 测试

后端测试至少覆盖：

- 生成验证码为 6 位数字；
- Redis 中不保存明文验证码；
- 60 秒内重复发送被拒绝；
- 同邮箱每日限额；
- 同 IP、设备、全站限额；
- 错误验证码 5 次后失效；
- 验证码成功后不可重复使用；
- SMTP 失败时不创建用户并清理验证码；
- 注册成功后写入 `email_verified_at`；
- 管理员引导账号写入 `email_verified_at`。

前端测试覆盖：

- 发送成功后出现 60 秒倒计时；
- 倒计时期间按钮不可点击；
- 邮箱验证码为空时不能注册；
- 发送失败后重新加载图形验证码；
- 注册成功后跳转原有目标页面。

## 发布顺序

1. 部署数据库迁移；
2. 写入 SMTP 环境变量；
3. 部署后端和前端；
4. 使用真实 QQ 邮箱完成一次注册验证；
5. 检查 DirectMail 发送统计和失败原因；
6. 观察 24 小时后再调整限额。
