# Quickstart Validation

## Backend

```powershell
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'
mvn -f backend/pom.xml -Dtest=AdminPointGrantApiTest test
Remove-Item Env:\SHUATI_DB_PASSWORD
```

预期：

- 管理员查询邮箱成功，响应邮箱已脱敏。
- 未登录返回 401，普通用户返回 403。
- 合法赠点后余额、点流水和赠点审计记录一致。
- 重复幂等键只到账一次。
- 超过上限、小数、空原因和不存在账号均被拒绝。

## Frontend

```powershell
cd frontend
npx playwright test tests/mobile-responsive.spec.ts --grep "admin point grant"
npm run build
```

预期：

- 后台存在“赠送点数”入口。
- 查询后显示账号摘要、余额和近期流水。
- 提交成功显示新余额，错误输入在页面内提示。
- 提交中按钮禁用。

## Production Smoke Test

1. 管理员登录后进入“赠送点数”。
2. 输入测试账号完整邮箱并查询。
3. 赠送 1 点，原因填写“功能验收”。
4. 确认成功提示、目标余额增加、用户点流水出现“管理员赠送”。
5. 使用同一页面刷新后确认余额未重复增加。
