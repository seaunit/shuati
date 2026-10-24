# Quickstart Validation

## Prerequisites

- MySQL 8.4 的 `shuati_test` 数据库可用。
- Redis 可用。
- 本地后端测试环境变量 `SHUATI_DB_PASSWORD` 已设置。

## Backend

```powershell
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'
mvn -f backend/pom.xml -Dtest=ImportReuseRetentionTest,ImportTargetBankTest test
Remove-Item Env:\SHUATI_DB_PASSWORD
```

预期：

- 同一来源可以多次创建执行记录。
- 同题库重复题干只保留一道题。
- 不同题库可导入相同题干。
- 用户成功导入记录不超过 10 条。
- 旧来源被清理后，已导入题目仍存在。

## Frontend

```powershell
cd frontend
npx playwright test tests/mobile-responsive.spec.ts --grep "import"
npm run build
```

预期：

- 已完成导入的来源仍显示题目和选择操作。
- 按钮显示“再次导入”。
- 页面展示新增数量与跳过数量。
- 导入执行中按钮禁用。

## Production Smoke Test

1. 从历史任务打开一条已完成解析记录。
2. 选择不同目标题库并提交再次导入。
3. 确认结果提示中新增数与跳过数之和等于选择数。
4. 再次导入同一题库，确认新增数为 0。
5. 查看历史列表，确认成功记录最多 10 条。
