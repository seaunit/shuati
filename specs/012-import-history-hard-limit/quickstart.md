# Quickstart Validation

## Backend

```powershell
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'
mvn -f backend/pom.xml -Dtest=ImportReuseRetentionTest test
Remove-Item Env:\SHUATI_DB_PASSWORD
```

预期：

- 同一用户创建 12 条不同状态来源后只保留 10 条。
- 最旧来源及其执行记录被删除。
- 已导入题目保持不变。
- 其他用户历史不受影响。

## Regression

```powershell
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'
mvn -f backend/pom.xml test
Remove-Item Env:\SHUATI_DB_PASSWORD
npm --prefix frontend run build
```

## Production

1. 重新部署服务。
2. 打开 `/app/import`，确认历史任务最多 10 条。
3. 新建一次解析任务，确认新增后仍不超过 10 条。
4. 打开原来已导入的题库，确认题目数量和内容未减少。
