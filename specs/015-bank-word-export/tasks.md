# Tasks: 题库导出 Word

**Input**: `specs/015-bank-word-export/`

## Phase 1: Setup

- [x] T001 确认 Apache POI 依赖、题库读取结构和下载响应方式

---

## Phase 2: Foundational

- [x] T002 在 `backend/src/test/java/com/shuati/bank/BankWordExportTest.java` 先补失败测试：
  本人/他人/管理员权限、DOCX 魔数、A4 页面、题库/单元/题目/答案解析内容、空题库
- [x] T003 在 `frontend/tests/mobile-responsive.spec.ts` 先补失败回归：
  题库管理页显示“导出 Word”并触发 `.docx` 下载

---

## Phase 3: User Story 1 - 从题库管理导出完整 Word (P1)

**Goal**: 有维护权限的用户单击一次即可获得自动排版的题库 Word 文档。

**Independent Test**: 导出含多单元、多题型和解析的题库，POI 回读断言内容与页面尺寸。

### Implementation

- [x] T004 [US1] 新增 `backend/src/main/java/com/shuati/bank/BankWordExportService.java`：
  查询题库、单元和题目，生成 A4 DOCX、中文标题层级、选项、答案和解析
- [x] T005 [US1] 在 `backend/src/main/java/com/shuati/bank/BankManageController.java` 增加
  `GET /api/manage/banks/{id}/export.docx`，复用 `requireManage` 并加用户级限流
- [x] T006 [US1] 在 `frontend/src/views/BankManageView.vue` 增加“导出 Word”入口和下载状态
- [x] T007 [US1] 更新 `specs/002-android-client/contracts/api-reuse.md` 记录导出契约

### Verification

- [x] T008 [US1] 运行 `BankWordExportTest`，覆盖权限矩阵和文件内容
- [x] T009 [US1] 运行 Playwright 导出用例，确认下载文件名与扩展名
- [x] T010 [US1] 运行 `npm --prefix frontend run build`
- [x] T011 [US1] 用水印/预览工具或 POI 测试验证文档可打开且无内容溢出

---

## Phase 4: Polish

- [x] T012 运行后端相关全量测试与前端全量 E2E
- [x] T013 提交推送 `main` 与 `test`
- [x] T014 部署生产并下载一次 Word 验证线上响应

---

## Dependencies

1. T001 → T002/T003
2. T002/T003 失败 → T004 → T005/T006
3. T005/T006 → T008/T009
4. T008/T009/T010/T011 → T012
5. T012 → T013 → T014

## MVP

完成 T004-T009 即具备可下载、可打开、权限正确的题库 Word 导出能力。
