# Tasks: 答案与 AI 解析语音朗读

**Input**: `specs/021-answer-speech/`

## Phase 1: Setup

- [x] T001 新增语音脚本类型与 Markdown 清洗、分句工具 `frontend/src/components/speechText.ts`
- [x] T002 为 `speechText.ts` 增加可测试的纯函数导出，覆盖代码块、链接、列表和长句
- [x] T003 [P] 在 `frontend/tests/answer-speech.spec.ts` 建立语音合成 mock 和答题页路由夹具

## Phase 2: Foundational

- [x] T004 编写 Markdown 转朗读脚本测试，覆盖默认跳过代码与开启朗读代码
- [x] T005 实现正文清洗、代码段识别、语义分句和最大句长切分
- [x] T006 [P] 定义 `SpeechSegment`、`SpeechState`、`SpeechPreference` 类型

## Phase 3: User Story 1 - 朗读答题反馈 (P1)

**Goal**: 用户可以完整播放、暂停、继续和停止答题反馈。

**Independent Test**: 提交答案后点击朗读，确认当前句逐段推进且暂停继续正常。

- [x] T007 [US1] 先写 Playwright 测试，覆盖开始朗读、暂停、继续和停止
- [x] T008 [US1] 实现 `AnswerSpeechPlayer.vue` 的基础状态机与中文语音选择
- [x] T009 [US1] 实现逐句播放、当前句展示和播放进度
- [x] T010 [US1] 在 `PracticeView.vue` 答题结果区接入播放器
- [x] T011 [US1] 在背题模式答案解析区接入同一播放器

## Phase 4: User Story 2 - 控制语速和阅读范围 (P2)

**Goal**: 用户可调整语速并控制代码块朗读。

**Independent Test**: 切换四档语速和代码开关，语音脚本与 utterance 参数同步更新。

- [x] T012 [US2] 先写 Playwright 测试，覆盖四档语速、偏好保存和代码开关
- [x] T013 [US2] 实现语速菜单和 `sessionStorage` 偏好保存
- [x] T014 [US2] 实现“朗读代码”开关并重建脚本
- [x] T015 [US2] 增加当前句高亮与 `aria-live` 状态

## Phase 5: User Story 3 - 稳定处理异常和切题 (P3)

**Goal**: 切题、离页、重新生成解析和无中文语音时状态可控。

**Independent Test**: 播放中切题、隐藏页面和模拟无语音环境，确认播放停止且提示明确。

- [x] T016 [US3] 先写 Playwright 测试，覆盖切题停止、页面隐藏和无中文语音
- [x] T017 [US3] 监听 `contentKey`、组件卸载和 `visibilitychange`，自动取消播放
- [x] T018 [US3] 实现无语音、无中文音色和 utterance 异常提示
- [x] T019 [US3] 重新生成解析后刷新朗读脚本并停止旧播放

## Phase 6: Polish

- [x] T020 运行 `npm --prefix frontend run build`
- [x] T021 运行 `npx playwright test tests/answer-speech.spec.ts`
- [x] T022 运行相关现有刷题 Playwright 回归
- [x] T023 [P] 更新 `specs/021-answer-speech/quickstart.md` 的测试结果
- [x] T024 提交测试分支并推送
- [ ] T025 部署测试环境并完成 Web/Android 真机验收

## Phase 7: 音色可用性修复

- [x] T026 先写 Playwright 用例，模拟默认在线音色返回语音合成错误并要求自动降级
- [x] T027 将语音专用失败音色从当前会话选项中移除，并持久化到 `sessionStorage`
- [x] T028 自动切换到其他可用中文音色，从当前句继续朗读
- [x] T029 区分自动播放限制与音色损坏，避免误隐藏可用音色
- [x] T030 运行语音专项、刷题回归和前端生产构建
- [x] T031 有本地中文音色时主动隐藏 Online/Natural 在线音色，并补充 Playwright 回归

## Phase 8: 后台窗口连续播放

- [x] T032 先写 Playwright 用例，确认 `visibilitychange` 不触发语音取消
- [x] T033 移除页面对 `visibilitychange` 的主动停止逻辑
- [x] T034 保留切题、脚本更新和组件卸载时的停止行为
- [x] T035 更新规格和验收说明，明确桌面浏览器与移动端系统策略差异
- [x] T036 运行语音专项、刷题回归和前端生产构建

## Dependencies

- T001-T006 阻塞全部用户故事。
- T007-T011 是首个可交付 MVP。
- T012-T015 依赖播放器状态机。
- T016-T019 必须在测试环境部署前完成。
- T020-T025 在功能通过后执行。
