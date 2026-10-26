# Quickstart: 验证题库 Word 导出

## Automated

```powershell
# 启动本机 MySQL 和 Redis后
mvn -f backend/pom.xml -Dtest=BankWordExportTest test

cd frontend
npx playwright test tests/mobile-responsive.spec.ts --grep "export word"
npm run build
```

## Manual

1. 登录有题库维护权限的账号。
2. 进入任一题库的“题库管理”页面。
3. 点击“导出 Word”。
4. 确认下载文件能被 Microsoft Word 或 WPS 打开。
5. 检查标题、单元、题型、选项、答案、解析和空字段处理。
