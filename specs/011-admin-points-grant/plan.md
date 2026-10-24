# Implementation Plan: 管理员后台赠送点数

**Branch**: `011-admin-points-grant` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-admin-points-grant/spec.md`

## Summary

为管理员后台增加独立的赠点页。管理员按完整邮箱查询账号，确认状态、余额和近期流水后提交赠点。后端通过新增的赠点审计表和点数流水在同一事务内完成幂等校验、账户行锁、余额增加与操作追溯。

## Technical Context

**Language/Version**: Java 17；Vue 3 + TypeScript

**Primary Dependencies**: Spring Boot 3.5、Spring Security、JdbcTemplate、Flyway、JUnit 5、Playwright

**Storage**: MySQL 8.4；新增 `admin_point_grant`，复用 `point_account` 与 `point_ledger`

**Testing**: JUnit 覆盖权限矩阵、幂等、校验与事务结果；Playwright 覆盖查询、赠点、错误和余额刷新；前端生产构建

**Target Platform**: Linux 生产服务器；Web 与 Capacitor 共用同一前端

**Project Type**: Spring Boot 后端 + Vue 前端

**Performance Goals**: 单次查询和赠点在常规数据库延迟内完成；管理端查询与赠点分别限流

**Constraints**: 仅管理员可访问；单次 1 至 1,000,000 点；原因 2 至 100 字；事务内行锁；同管理员同幂等键只到账一次

**Scale/Scope**: 管理员用户管理、点账户与点流水模型、后台 `AdminView`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I 用户数据与密钥**：通过。仅管理员可查询目标账号；响应邮箱脱敏；日志不输出敏感信息。
- **II Flyway 迁移**：通过。新增 `V20`，不改写既有迁移。
- **III 测试与证据**：通过。补充 JUnit 权限/幂等/账本测试、Playwright 交互测试和构建验证。
- **IV 收录边界**：通过。后台接口不进入公开页面或 sitemap。
- **V 点数与 AI**：通过。余额变化与点流水同事务；账户行锁；幂等键防重复；不涉及 AI 调用。
- **VI 客户端同源**：通过。只修改网页后台，移动端壳复用同一前端。
- **部署与资源约束**：通过。不新增后台任务，仅增加管理员操作接口。

## Project Structure

### Documentation

```text
specs/011-admin-points-grant/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── admin-points-api.md
└── checklists/
    └── requirements.md
```

### Source Code

```text
backend/
├── src/main/java/com/shuati/admin/AdminPointGrantService.java
├── src/main/java/com/shuati/admin/AdminUserController.java
├── src/main/resources/db/migration/V20__admin_point_grants.sql
└── src/test/java/com/shuati/admin/AdminPointGrantApiTest.java

frontend/
├── src/views/AdminView.vue
├── src/views/PricingView.vue
└── tests/mobile-responsive.spec.ts
```

**Structure Decision**: 赠点业务放入现有管理员模块；`PointAccountService` 继续作为点账户底层能力，不把 HTTP 查询和邮箱解析塞进计费服务。

## Design Decisions

1. `admin_point_grant` 记录幂等键、管理员快照、目标用户、点数、原因和关联流水。
2. 赠点事务先锁管理员账号，再锁目标点账户；同一管理员请求串行化，防并发重复插入。
3. 幂等键必须是 UUID。重复键和相同载荷返回首次结果；相同键但载荷不同返回冲突。
4. 点数流水 reason 固定为 `ADMIN_GRANT`，note 保存管理员填写的原因，`ref_type=admin_point_grant`，`ref_id=赠点记录 ID`。
5. 邮箱查询不区分大小写，页面只展示脱敏邮箱。
6. 旧 `/api/admin/users/{id}/points` 的 `GRANT` 分支停止直接加减余额，引导使用新赠点接口；套餐调整分支保留。

## Complexity Tracking

无宪法例外。
