# 刷题系统

基于 Next.js 15 + Supabase 的刷题与题库管理系统，部署在 Netlify。

## 功能

- 题库 / 单元分类，支持公共题库与账号私有题库
- 单元题、多选题、简答题三种题型，图片题面展示
- 选择题本地判分，简答题与解析按需调用 AI
- 刷题进度记录、错题本、练习评分与练习记录
- 题库导入：文档（Word / PPT / PDF）、粘贴正文、网页链接，后台异步解析
- 后台管理：题库、单元、题目、账号、AI 模型配置、数据统计与 Token 用量

## 本地开发

```bash
npm install
cp .env.example .env.local   # 填写 Supabase / DeepSeek 配置
npm run dev
```

访问 http://localhost:3000 。

## 环境变量

部署环境（Netlify）需要配置：

- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `DEEPSEEK_API_KEY`
- `DEEPSEEK_BASE_URL`
- `DEEPSEEK_MODEL`
- `APP_AES_SECRET`

仓库内不保存任何密钥，`.env.local` 已被 `.gitignore` 忽略。

## 维护脚本

`scripts/` 下的脚本从环境变量读取凭据（也会自动加载 `.env.local`）：

- `ensure-admin.cjs`：创建或重置管理员账号，需要 `ADMIN_PASSWORD`
- `smoke-api.cjs`：本地接口冒烟测试，需要 `SMOKE_PASSWORD`
- `introspect.cjs` / `apply-import-migration.cjs`：数据库结构检查与迁移，需要 `SUPABASE_DB_URL`

## 数据库

数据库迁移文件位于 `supabase/migrations/`。

## 商业化（套餐与点数）

公共题库与选择题本地判分永久免费，AI 能力按点数计费。

**点数规则**（后台可调，见 `ai_price_rule`）：

| 功能 | 消耗 |
| --- | --- |
| 选择题 AI 解析 | 1 点 / 次 |
| 简答题 / 伪代码 AI 判分 | 3 点 / 次 |
| 文档 / 网页 / 正文解析生成题库 | 10 点 / 1 万字 |

**账户结构**：点数分两个桶，`MONTHLY`（订阅周期额度，到期重置）与 `BONUS`
（加量包与赠送，不随周期重置）。扣费优先扣 `MONTHLY`，再扣 `BONUS`；
AI 调用失败自动退点，导入任务按失败切片比例退点。

**套餐**：免费版 / Plus / Pro / 机构版，档位、价格、额度与题库上限都存在
`public.plan` 表，可在后台「套餐与点数」中直接调整。

**支付**：订单表 `subscription_order` 已就绪，但**尚未接入在线支付**。
当前流程是用户下单生成待支付订单，管理员确认收款后在后台点「已收款」，
系统自动发放点数或开通套餐。接入微信 / 支付宝 / Stripe 时，只需在支付回调中
调用同样的结算逻辑。

**迁移**：`supabase/migrations/20261006000000_billing.sql`。
该迁移尚未在此仓库的部署流程中自动执行，需手动应用；未应用时后端会自动
跳过扣费（fail-open），AI 功能仍可用。
