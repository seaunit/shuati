# Tasks: 管理员账号名登录

## Phase 1: Implementation

- [x] T001 更新 `frontend/src/views/LoginView.vue`：登录模式允许邮箱或账号
- [x] T002 更新 `backend/src/main/java/com/shuati/config/AdminBootstrap.java`：兼容非邮箱账号
- [x] T003 更新 `backend/src/main/java/com/shuati/auth/AuthService.java`：统一账号错误提示
- [x] T004 更新 `deploy/scripts/provision-ubuntu.sh`：默认账号及已有配置保留
- [x] T005 补 Playwright 与 JUnit 回归测试

## Phase 2: Environment

- [x] T006 更新生产管理员账号、BCrypt 密码和会话版本
- [x] T007 更新 `/etc/shuati/env`，保持权限 600
- [x] T008 更新本地测试库和 `.env.local`

## Phase 3: Verification

- [x] T009 后端全量测试通过（155 条）
- [x] T010 前端生产构建通过
- [x] T011 登录相关 Playwright 通过
- [x] T012 生产真实登录并验证 `ADMIN` 身份
- [x] T013 推送 `main` 与 `test`
