# Quickstart: 验证刷题范围恢复

## Automated

```powershell
# 后端最近范围与权限测试
# 先按 README 设置本机 SHUATI_DB_PASSWORD 环境变量
mvn -f backend/pom.xml -Dtest=PracticeLastScopeTest test

# 前端回归与构建
npx --prefix frontend playwright test frontend/tests/mobile-responsive.spec.ts --grep "last practice scope"
npm --prefix frontend run build
```

## Manual

1. 登录账号，在「JAVA 八股」题库练习并跳到任意题。
2. 离开刷题页，再进入一个「全部题目」范围，制造较新的 `ALL` 记录。
3. 回到首页，点击侧栏或底部导航「刷题」。
4. 页面 URL 应带原题库的 `bankId`，题目总数与题号应属于该题库。
5. 打开带明确 `unitId` 或 `ids` 的链接，确认仍进入指定范围。
