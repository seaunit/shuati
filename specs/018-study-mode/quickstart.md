# Quickstart: 背题模式

```powershell
mvn -f backend/pom.xml -Dtest=PracticeStudyAnswerTest test
cd frontend
npx playwright test tests/mobile-responsive.spec.ts --grep "study mode"
npm run build
```

手工验证：开启背题模式，确认不提交也显示答案；有解析不扣点，无解析自动生成并扣点。
