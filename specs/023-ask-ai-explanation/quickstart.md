# Quickstart: 答案解析问 AI

## Prerequisites

- MySQL、Redis 和本地后端已启动。
- Dify 已导入并发布 `shuati-ask` 工作流。
- `.env.local` 已设置 `DIFY_WORKFLOW_ASK_TOKEN`。

## Backend

```powershell
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'
mvn -f backend/pom.xml test
Remove-Item Env:\SHUATI_DB_PASSWORD
```

重点用例：

- 公共题 / 本人私有题可追问。
- 他人私有题返回 403，且无 AI 调用、无扣点。
- 成功回答扣 1 点并写 `AI_ASK` 流水和 `AI` 调用日志。
- AI 失败、空回答或超时全额退回。
- 同一 `requestId` 重放不重复扣点。
- 余额不足和超频在调用 AI 前终止。

## Frontend

```powershell
npm --prefix frontend run build
cd frontend
npx playwright test tests/mobile-responsive.spec.ts
```

页面验收：

1. 未提交答案时不显示问 AI。
2. 提交后解析区域显示“问 AI”和“每次追问 1 点”。
3. 发送问题后显示用户消息、加载状态和 Markdown 回答。
4. 同题连续追问保留上下文；切题后对话清空。
5. 失败提示明确，余额和顶部点数同步刷新。
6. 手机宽度下输入框、消息和按钮不溢出，底部操作栏不遮挡输入区域。

## Dify

```powershell
pwsh -File deploy/scripts/dify-local.ps1 status
python -m unittest deploy/scripts/test_dify_prod_bootstrap.py -v
```

生产部署时 `provision-ubuntu.sh` 自动同步并发布 `shuati-ask`，并执行五类工作流冒烟。
