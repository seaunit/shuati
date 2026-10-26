# Tasks: 试卷原题保真解析

## Phase 1: Setup

- [x] T001 对比原图、现有解析结果和历史 `import_task`，记录题目数量与错误拆题位置

## Phase 2: Foundational

- [x] T002 在 `backend/src/test/java/com/shuati/ai/PromptTemplatesTest.java` 先补失败测试：
  提示词必须包含试卷模式、禁止生成衍生题、无选项用 SHORT、题内小问不拆题、共享表格保留

## Phase 3: User Story 1 - 试卷只抽取，不扩写 (P1)

- [x] T003 [US1] 重写 `backend/src/main/java/com/shuati/ai/PromptTemplates.java` 的 `EXTRACT_SYSTEM`
- [x] T004 [US1] 强化 `EXTRACT_IMAGE_USER`，明确纸质试卷/面试题的抽取规则
- [x] T005 [US1] 保留 `ImportService` 单次 AI 调用、点数和历史契约不变

### Verification

- [x] T006 [US1] 运行提示词与导入相关 JUnit
- [x] T007 [US1] 用用户已有三张截图重新解析：分别应为 4、1、3 道题
- [x] T008 [US1] 检查算法题为一道 SHORT，包含输入、返回、打印和 sqr() 要求
- [x] T009 [US1] 检查项目二选一为两道 SHORT，每题包含完整业务背景与需求
- [x] T010 [US1] 检查 SQL 题均带完整表 A/表 B 上下文

## Phase 4: User Story 2 - 普通资料仍可生成题 (P2)

- [x] T011 [US2] 用一段普通笔记做提示词/模型回归，确认仍能生成 5-10 题

## Phase 5: Delivery

- [x] T012 运行后端全量测试
- [ ] T013 提交推送 `main` 与 `test`
- [ ] T014 部署生产并确认真实图片解析结果

## Dependencies

1. T001 → T002
2. T002 失败 → T003/T004
3. T003/T004 → T005 → T006
4. T006 → T007-T011
5. 全部通过 → T012-T014
