# Quickstart: 代码题解析

```powershell
mvn -f backend/pom.xml -Dtest=PromptTemplatesTest test
cd frontend
npx playwright test tests/mobile-responsive.spec.ts --grep "code answer"
npm run build
```

手工验证：提交一份算法简答题，确认参考答案和 AI 评语中的伪代码均显示为独立代码块。
