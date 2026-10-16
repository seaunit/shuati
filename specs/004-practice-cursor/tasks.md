# Tasks: 练习光标

**Input**: [spec.md](./spec.md)、[plan.md](./plan.md)

## Phase 1: 后端

- [x] T001 迁移 `V17__practice_cursor.sql`：新表 + 两个外键 + 范围类型约束
- [x] T002 `BankService`：`cursor(scopeType, scopeId)` 读、`saveCursor(scopeType, scopeId, questionId)` 写/清，
  含范围可见性校验与 `insert ... on duplicate key update` 幂等写
- [x] T003 `BankController`：`GET /api/progress/cursor`、`PUT /api/progress/cursor`（写接口带限流）
- [x] T004 `PracticeCursorTest`：7 条（写入后读到 / 范围隔离 / 清空 / 跨账号不可见 / 范围越权 / 非法范围与题目 / HTTP 层读写）

## Phase 2: 网页端

- [x] T005 `PracticeView.vue`：进入时优先按光标落位（拿不到才回退"第一道没答过的题"，两段各自容错）
- [x] T006 `PracticeView.vue`：切题（下一题/上一题/从头开始/落位）写光标；完成练习清空；错题重练不碰光标
- [x] T007 Playwright：42 条全绿（新增"三道题全答过也按光标落在第 2 题"+ 断言切题写回光标）

## Phase 3: 安卓端

- [x] T008 `BankRepository`：`cursor()` / `saveCursor()`
- [x] T009 `ResumeLocator.startIndex(questions, cursorId, answeredIds)`：光标优先 → 老规则回退
- [x] T010 `PracticeState`：载入按 startIndex 落位；切题写光标；完成清空；错题重练跳过
- [x] T011 JUnit：27 条全绿（新增光标优先 / 失效光标回退 / 无光标回退）

> 说明：客户端已换成 Capacitor 壳（加载网页端），所以**网页端的改动就是 App 的行为**；
> 原生那份已退役，这里同步改它是为了让仓库里两份实现口径一致，不再单独出包。

## Phase 4: 契约与交付

- [x] T012 `specs/002-android-client/contracts/api-reuse.md` 补两个接口（两端通用契约）
- [x] T013 证据：后端 `mvn test` **113 条全绿**（含 V17 迁移执行成功）；前端 `npm run build` 通过 + Playwright 42 条全绿；
  安卓 27 条单测全绿 + `assembleDebug` 通过
- [ ] T014 部署后验收用户描述的那条流程（答一题 → 从头开始 → 练到第 2 题 → 退出 → 重进停在第 2 题）
