# Implementation Plan: 管理员账号名登录

**Branch**: `014-admin-username-login` | **Date**: 2026-10-26

## Summary

登录接口原本没有邮箱格式校验，但前端邮箱输入框和管理员引导的昵称截取只支持邮箱。
本次放开登录模式的输入格式，并让管理员引导兼容普通账号名；注册和找回密码保持邮箱约束。

## Technical Context

- **Frontend**: Vue 3 + TypeScript，LoginView 根据模式切换 `text` / `email`
- **Backend**: Spring Boot + JPA，登录继续复用 `profiles.email` 唯一字段
- **Database**: 无 schema 变更；更新管理员账号、BCrypt 哈希和会话版本
- **Deployment**: 首次部署默认账号改为 `a115846`，已有配置优先保留
- **Verification**: JUnit、Playwright、生产真实登录

## Constitution Check

- 密钥与用户数据：通过，明文密码不进入 Git。
- 数据库迁移：通过，无 schema 变更。
- 测试证据：通过，后端与前端的账号格式测试已补。
- 客户端同源：通过，Web 与 Capacitor App 共用同一登录页和后端接口。

## Affected Files

- `frontend/src/views/LoginView.vue`
- `frontend/tests/mobile-responsive.spec.ts`
- `backend/src/main/java/com/shuati/auth/AuthService.java`
- `backend/src/main/java/com/shuati/config/AdminBootstrap.java`
- `backend/src/test/java/com/shuati/config/AdminBootstrapUsernameTest.java`
- `deploy/scripts/provision-ubuntu.sh`

## Risks

- 普通账号名仍存于 `profiles.email` 字段，这是兼容现有唯一索引与登录接口的保守实现。
- 修改账号后必须递增 `session_version`，否则旧 Cookie 仍可能有效。
- 生产密码仅由服务器配置和数据库哈希承载，后续重部署必须保留现有值。
