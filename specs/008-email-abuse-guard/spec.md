# Feature Specification: 注册邮箱风控（临时邮箱拦截 + 反脚本）

**Feature Branch**: `008-email-abuse-guard`

**Created**: 2026-10-20

**Status**: Draft

**Input**: 用户要求 —— 「写个规则或者方案，过滤掉这种临时邮箱，可考虑布隆过滤器什么的防止脚本攻击薅羊毛」。

**背景**：线上出现 `daniel.parkerca61598b@mailinator.com` 这类临时邮箱注册。Mailinator 的收件箱是
**公开可读**的，邮箱验证码对它形同虚设；同类的批量注册还可能刷注册赠点与月度补点。

## 问题与边界

- 临时邮箱不是「无效邮箱」，而是「**谁都能打开收件箱**的邮箱」，所以只靠邮箱验证码挡不住。
- 脚本注册成本极低，单靠域名黑名单会被换域名绕过，必须叠加**频率**与**额度**两道闸。
- 本期只处理注册/发码这条链路，不改已有账号、不改点数账本规则。

## Requirements *(mandatory)*

- **FR-001**: 注册链路（发邮箱验证码 + 提交注册）MUST 先校验邮箱域名；命中一次性邮箱名单时 MUST 拒绝，
  返回可读提示（如「请使用常用邮箱，临时邮箱暂不支持」），MUST NOT 继续发码或建号。
- **FR-002**: 域名匹配 MUST 先归一化：去首尾空格、转小写、去末尾点、IDN 转 punycode；
  MUST 支持后缀匹配（`x.mailinator.com` 命中 `mailinator.com`）；
  MUST NOT 误伤 `notmailinator.com`、`mailinator.com.cn` 这类相近但不相同的域名。
- **FR-003**: 名单 MUST 内置常见一次性邮箱服务（首版不少于 40 个），MUST 支持通过环境变量追加，
  MUST NOT 把名单写死在代码字符串里。
- **FR-004**: 每次拦截 MUST 记结构化日志：域名、注册 IP、User-Agent、脱敏邮箱；
  MUST NOT 记录明文密码或验证码。
- **FR-005**: 注册接口 MUST 按 IP 限流（默认 3 次/小时、10 次/天），超限统一返回「请求过于频繁」。
- **FR-006**: 注册 MUST 叠加图形验证码（复用现有 `/captcha/*`），与邮箱验证码同时校验。
- **FR-007**: 点数 MUST 只在邮箱验证通过后发放；验证通过前 MUST NOT 存在可消费点数。
- **FR-008**: 新注册账号 24 小时内 MUST 有独立 AI 调用上限（默认 30 次/天，可配置），超限提示稍后再试。
- **FR-009**: 管理员 MUST 能查到最近被拦截的注册（至少日志可检索；后台列表为可选加分项）。

## Success Criteria *(mandatory)*

- **SC-001**: `x@mailinator.com`、`X@Mailinator.COM`、`a@b.mailinator.com` 全部被拒；
  `a@notmailinator.com` 正常放行。
- **SC-002**: `qq.com` / `163.com` / `gmail.com` / `outlook.com` / 企业自有域名 均能正常注册。
- **SC-003**: 同一 IP 第 4 次注册请求返回 429；换 IP 后正常。
- **SC-004**: 未验证邮箱的账号点数为 0，无法调用 AI。
- **SC-005**: 新账号首日第 31 次 AI 调用被拒，次日恢复。
- **SC-006**: 拦截日志含域名与 IP，不含明文邮箱与密码。

## 附录：首版内置名单（按功能分类，实际以资源文件为准）

| 类别 | 域名示例 |
| --- | --- |
| 公开收件箱 | mailinator.com、mailinator.net、maildrop.cc、inboxkitten.com、harakirimail.com |
| 一次性邮箱 | guerrillamail.com、guerrillamail.info、sharklasers.com、10minutemail.com、temp-mail.org、tempmail.dev、tempail.com、mohmal.com |
| 别名转发 | yopmail.com、yopmail.fr、trashmail.com、mailnesia.com、spamgourmet.com、mytemp.email |
| 国内临时邮箱 | linshiyouxiang.net、24mail.chacuo.net、mailto.plus、snapmail.cc |
| 生成器域名 | fakemailgenerator.com、emailondeck.com、mailsac.com、tempinbox.com、throwawaymail.com |

> 名单会持续被绕过，**它只是第一道闸**；真正的防线是 FR-005（限流）、FR-006（验证码）、
> FR-007（验证后才给点数）、FR-008（新号 AI 额度）。
