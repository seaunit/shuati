# Quickstart: 试卷原题回归

## Automated

```powershell
mvn -f backend/pom.xml -Dtest=PromptTemplatesTest test
mvn -f backend/pom.xml -Dtest=ImportScreenshotCreateTest test
```

## Real Model Regression

1. 从 `import_task` 中复用用户已有的三张截图来源。
2. 重新创建三次 screenshot 解析任务。
3. 断言三张图分别得到 4、1、3 道题。
4. 检查算法题是一道 `SHORT`，项目二选一为两道 `SHORT`。

## Manual

1. 上传三张原图。
2. 查看解析结果数量、题干和表格。
3. 确认没有新增“根据资料……”式衍生题。
