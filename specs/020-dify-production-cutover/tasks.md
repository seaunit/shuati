# Tasks: Dify 生产切换

**Input**: Design documents from `specs/020-dify-production-cutover/`

## Phase 1: Setup

- [ ] T001 创建生产 Dify 安装与运维脚本 `deploy/scripts/dify-prod.sh`
- [ ] T002 创建生产 Dify 初始化、工作流导入和 Token 脚本 `deploy/scripts/dify-prod-bootstrap.py`
- [ ] T003 [P] 更新 `deploy/env.example`、`deploy/README.md` 和 `docs/Dify本地测试环境.md` 的生产说明

## Phase 2: Foundational

- [ ] T004 在生产服务器安装 Python/Node/系统依赖、增加 2 GiB Swap，并确认现有服务仍在线
- [ ] T005 将 Dify 1.13.3 部署到 `/opt/dify`，只绑定 `127.0.0.1:8501`
- [ ] T006 [P] 将生产脚本与四个无密钥工作流同步到 `/opt/shuati`，检查 `/etc/shuati/env` 权限

## Phase 3: User Story 1 - 正式环境使用 Dify (P1)

**Goal**: 四个正式 Dify 工作流可用，后端切换到 Dify。

**Independent Test**: 判题、解析、文本出题、截图出题各执行一次真实工作流。

- [ ] T007 [US1] 初始化生产 Dify 管理员并安装 DeepSeek 0.0.26 插件
- [ ] T008 [US1] 导入并发布四个工作流，配置 DeepSeek 模型凭证
- [ ] T009 [US1] 为四个应用创建 Service API Token，写入 `/etc/shuati/env`
- [ ] T010 [US1] 切换 `SHUATI_AI_ENGINE=dify` 并重启正式后端
- [ ] T011 [US1] 执行四类真实工作流冒烟并确认 `ai_call_log.provider=DIFY`

## Phase 4: User Story 2 - 安全上线与可回滚 (P2)

**Goal**: 密钥不泄露，控制台不公开，故障可快速回滚。

**Independent Test**: 端口扫描、密钥扫描和 `direct` 回滚操作全部通过。

- [ ] T012 [US2] 验证 8501 只监听回环地址且公网不可访问
- [ ] T013 [US2] 扫描仓库、构建产物和服务器日志，确认无真实密钥泄露
- [ ] T014 [US2] 备份 `/etc/shuati/env`、systemd、Nginx 与 Dify 原生配置
- [ ] T015 [US2] 演练 `SHUATI_AI_ENGINE=direct` 回滚并确认后端恢复

## Phase 5: User Story 3 - 正式发布验收 (P3)

**Goal**: 完整生产入口与关键业务链路可访问。

**Independent Test**: 公网页面、健康检查和登录入口全部通过。

- [ ] T016 [US3] 检查正式首页、登录页、价格页、下载页和 Sitemap
- [ ] T017 [US3] 检查后端健康、支付侧车健康、Nginx、MySQL、Redis 和 Dify systemd 服务状态
- [ ] T018 [US3] 确认正式账号可登录且页面调用 AI 功能成功

## Phase 6: Polish

- [ ] T019 运行后端全量测试和前端生产构建
- [ ] T020 [P] 记录 Dify 启停、升级、日志、备份和回滚命令
- [ ] T021 提交并推送 `main`，保持 `test` 分支环境差异不污染正式分支
- [ ] T022 输出最终上线证据、生产链接与已知风险

## Dependencies

- T004-T006 阻塞全部用户故事。
- T007-T009 阻塞 T010-T011。
- T012-T015 可在 Dify 启动后与 US1 并行验证。
- T016-T018 必须在正式后端切换后执行。
- T019-T022 在业务验收完成后执行。
