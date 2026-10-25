# Tasks: 刷题入口恢复上次练习范围

**Input**: Design documents from `specs/013-practice-scope-resume/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/

## Phase 1: Setup

- [x] T001 确认现有练习会话、练习光标与路由入口的数据流，记录根因到 `specs/013-practice-scope-resume/research.md`

---

## Phase 2: Foundational

- [x] T002 在 `backend/src/test/java/com/shuati/practice/PracticeLastScopeTest.java` 先补失败测试：
  最近 `ALL` 不抢占题库/单元、普通用户/本人/管理员可见性、无有效范围返回空
- [x] T003 在 `frontend/tests/mobile-responsive.spec.ts` 先补失败回归：
  裸 `/app/practice` 恢复最近题库且不请求全量题

---

## Phase 3: User Story 1 - 从侧栏继续上次题库 (P1)

**Goal**: 点击裸「刷题」入口时恢复最近有效题库/单元，并继续该范围题号。

**Independent Test**: 同时准备较新的 `ALL` 记录和较旧的 `BANK` 记录，进入裸入口后加载题库题目。

### Implementation

- [x] T004 [US1] 在 `backend/src/main/java/com/shuati/practice/PracticeService.java` 实现
  `latestScopedSession()`：只取当前账号最近 `BANK`/`UNIT`，校验范围存在且可见
- [x] T005 [US1] 在 `backend/src/main/java/com/shuati/practice/PracticeController.java` 增加
  `GET /api/practice/last-scope`
- [x] T006 [US1] 在 `frontend/src/router/index.ts` 为裸 `/app/practice` 查询最近范围并补
  `bankId`/`unitId`；显式 `bankId`、`unitId`、`ids` 不调用、不覆盖
- [x] T007 [US1] 接口失败、空范围、失效范围时回退全量题，不阻断练习
- [x] T008 [US1] 更新 `specs/002-android-client/contracts/api-reuse.md`，记录 Web/App 共用契约

### Verification

- [x] T009 运行 `PracticeLastScopeTest`，覆盖本人/他人/管理员与回退矩阵
- [x] T010 运行 Playwright 目标用例，确认题库恢复成功且未请求 `/api/questions?mode=seq`
- [x] T011 运行相关练习测试，确认显式范围、错题重练、光标恢复无回归

---

## Phase 4: Polish

- [x] T012 运行 `npm --prefix frontend run build`
- [x] T013 运行后端相关全量测试并记录证据
- [x] T014 运行 `$speckit-analyze`，确认 spec/plan/tasks/code 一致
- [ ] T015 提交推送并部署后验证裸入口恢复最近题库

---

## Dependencies & Execution Order

1. T001 → T002/T003（先确认并写失败测试）
2. T002/T003 失败 → T004/T005/T006
3. T004/T005 → T009
4. T006/T007 → T010/T011
5. 全部实现 → T012/T013/T014
6. 验证通过 → T015

## Parallel Opportunities

- T002 与 T003 可并行，分别位于后端与前端测试文件。
- T004/T005 与 T006/T007 在后端契约确定后可并行。

## MVP

完成 Phase 3 即可交付核心修复；Phase 4 用于构建、一致性检查和上线闭环。
