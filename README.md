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
