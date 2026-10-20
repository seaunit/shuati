# Implementation Plan: 注册邮箱风控

**Spec**: [spec.md](./spec.md)

## 技术方案

| 事项 | 做法 |
| --- | --- |
| 名单来源 | `backend/src/main/resources/abuse/disposable-domains.txt`（每行一个域名，`#` 注释）+ 环境变量 `SHUATI_BLOCKED_EMAIL_DOMAINS`（逗号分隔追加） |
| 匹配器 | 新增 `EmailDomainGuard`：归一化（trim → lowercase → 去尾点 → IDN 转 punycode）+ 后缀匹配；启动时加载为 `Set<String>`，判断 O(1)，启动日志打印条数 |
| 注册链路 | `AuthService.register` 与「发送邮箱验证码」接口各校验一次（发码前就挡住，避免浪费邮件配额） |
| 限流 | 小时级复用 `@RateLimit`（IP 维度）；日级用 Redis 计数器 `reg:ip:{ip}:{yyyyMMdd}`，TTL 到当天 24:00 |
| 图形验证码 | 注册请求体增加 `captchaTicket` / `captchaCode`，复用现有 `CaptchaService` |
| 新号 AI 额度 | AI 调用入口先看 `profiles.created_at`；24 小时内按 `ai_call_log` 计数，超限拒绝 |
| 可观测 | 拦截时 `log.warn("register.blocked domain={} ip={} ua={}", ...)`；邮箱脱敏为 `d***@domain` |
| 名单更新 | 随发版更新资源文件；一期不做后台编辑，避免运营改坏 |

## 为什么一期不用布隆过滤器

1. **规模不匹配**：名单是几百到几千条，`HashSet` + 后缀匹配内存不到 1 MB，单次判断纳秒级；布隆在这里没有收益。
2. **假阳性不可接受**：布隆只能说「可能存在」。注册是**不可逆动作**，典型 1% 假阳性 = 1% 的真实用户被挡在门外。
3. **不能删除**：名单纠错（误加的域名）只能整表重建。
4. **真正适合布隆的场景**：十万级名单的前置筛，或需要多实例共享的大集合。那时用 Redis 位图 + 布隆做**前置筛**，
   命中后**必须**回查精确集合/数据库确认，**绝不单独凭布隆拒绝**。

> 一句话：防脚本的关键在「频率 + 验证码 + 新号额度」，名单查询速度从来不是瓶颈。

## 影响面

| 位置 | 变更 | 风险 |
| --- | --- | --- |
| `backend/auth` | 注册/发码加校验与限流 | 中：注册是主链路，需覆盖回滚 |
| `backend/captcha` | 注册复用现有验证码 | 低 |
| `backend/resources/abuse` | 新增名单文件 | 低 |
| 配置 | 新增 4 个 env（见下） | 低，默认值保守 |
| `frontend` 注册页 | 增加图形验证码、错误提示 | 低 |

## 新增配置（默认值即上线值）

```bash
SHUATI_BLOCKED_EMAIL_DOMAINS=            # 追加域名，逗号分隔
SHUATI_REGISTER_IP_HOURLY_LIMIT=3
SHUATI_REGISTER_IP_DAILY_LIMIT=10
SHUATI_NEW_ACCOUNT_AI_DAILY_LIMIT=30     # 注册后 24 小时内
```

## 测试策略

- 单元：匹配器（大小写、子域、IDN、近似域名不误伤）、名单加载（空行/注释/重复/非法行）；
- 集成：临时邮箱注册被拒、正常邮箱注册成功、同 IP 第 4 次 429、未验证点数为 0、新号 AI 超限；
- 回归：登录、找回密码、登录图形验证码用例不变。
