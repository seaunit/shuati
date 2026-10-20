# Tasks: 注册邮箱风控

## Phase 1 · 名单与匹配器

- [x] T001 新增 `backend/src/main/resources/abuse/disposable-domains.txt`：首版不少于 40 个域名，
      每行一个、`#` 注释；启动时校验格式（非法行直接报错，避免静默失效）
- [x] T002 新增 `EmailDomainGuard`：归一化（trim / lowercase / 去尾点 / IDN→punycode）+ 后缀匹配 +
      环境变量追加；MUST NOT 用 `endsWith` 裸匹配（会误伤 `notmailinator.com`）
- [x] T003 单元测试：`x@mailinator.com`、`X@Mailinator.COM`、`a@b.mailinator.com` 命中；
      `a@notmailinator.com`、`a@mailinator.com.cn` 不命中；IDN 域名归一化

## Phase 2 · 注册链路

- [x] T004 发邮箱验证码接口接入 `EmailDomainGuard`（命中直接拒绝，不发信）
- [x] T005 `AuthService.register` 接入 `EmailDomainGuard`，错误码与前端提示对齐
- [x] T006 注册请求增加图形验证码（`captchaTicket` / `captchaCode`），复用 `CaptchaService`
- [x] T007 注册按 IP 限流：小时级 + 日级 Redis 计数器（`RegisterThrottle`，参数可配）
- [x] T008 拦截日志：域名 / IP / UA / 脱敏邮箱；禁止记录密码与验证码

## Phase 3 · 防薅羊毛

- [x] T009 确认点数发放时机：邮箱验证通过后才发；验证前点数为 0（补集成测试）
- [x] T010 新账号 24 小时内 AI 调用上限（默认 30 次/天），超限返回明确提示

## Phase 4 · 前端与验收

- [x] T011 注册页加图形验证码控件与错误提示（复用登录那套控件）
- [ ] T012 验收：SC-001 ~ SC-006 逐条跑一遍并记录结果；部署后观察 24 小时拦截日志

## 依赖与顺序

- T001 → T002 → T003；T004/T005 依赖 T002；T006 可与 T004/T005 并行
- T009 只补测试，不改逻辑；T010 依赖 T009 的计数口径
- T011 依赖 T006 的接口契约；T012 最后做
