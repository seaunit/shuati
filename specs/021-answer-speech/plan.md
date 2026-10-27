# Implementation Plan: 答案与 AI 解析语音朗读

**Branch**: `test` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

## Summary

在 Vue 前端新增可复用的答题反馈朗读组件。组件使用浏览器原生语音合成，将 Markdown 答案与解析转换为分句脚本，逐句播放并高亮当前句。支持语速、代码跳过、暂停继续、停止、切题复位、失败音色自动剔除与本地音色降级。后端、Dify、数据库和点数均不修改。

## Technical Context

**Language/Version**: Vue 3 + TypeScript 5 + Vite 6

**Primary Dependencies**: Browser `SpeechSynthesis` / `SpeechSynthesisUtterance`；现有 `lucide-vue-next`

**Storage**: `sessionStorage` 保存语速与代码偏好，不写业务数据库

**Testing**: Playwright 页面级测试，覆盖播放、暂停/继续、停止、切题、代码跳过和语音不可用

**Target Platform**: Web 与 Capacitor Android/iOS 壳

**Project Type**: 单套前端多端打包

**Performance Goals**: 控制操作 1 秒内反馈；单句不超过约 180 字；超长解析分段播放

**Constraints**: 不自动播放；不新增后端接口；不改变作答与点数流程；代码块默认跳过

**Scale/Scope**: 练习页答题反馈区和背题模式答案解析区

## Constitution Check

| 原则 | 结论 | 说明 |
| --- | --- | --- |
| I 密钥与用户数据 | PASS | 全部在浏览器本地完成，不上传朗读内容 |
| II 数据库迁移 | PASS | 无数据库变更 |
| III 测试与证据 | PASS | 新增 Playwright 用例并执行前端构建 |
| IV 收录边界 | PASS | 功能位于私有 `/app` 页面，不影响公开 SEO |
| V 点数与 AI | PASS | 语音朗读不调用 AI，不产生点数流水 |
| VI 客户端同源 | PASS | Web 与 Capacitor 共用同一组件和前端产物 |

## Structure

```text
frontend/src/components/
├── AnswerSpeechPlayer.vue       # 完整播放控件与状态机
└── speechText.ts                # Markdown 清洗、代码处理、分句

frontend/src/views/PracticeView.vue
frontend/tests/answer-speech.spec.ts
```

## Phase 0: Research

见 [research.md](./research.md)。采用设备原生 TTS，不依赖云端服务或新增依赖。

## Phase 1: Design

- 数据结构与状态见 [data-model.md](./data-model.md)。
- 组件契约见 [contracts/answer-speech.md](./contracts/answer-speech.md)。
- 验收流程见 [quickstart.md](./quickstart.md)。

## Risks

- 不同设备中文音色差异较大，需要语音列表降级策略。
- Windows/Chrome 可能列出实际不可用的在线自然音色，必须按语音专用错误动态剔除并自动重试。
- 部分 WebView 对长文本播放不稳定，必须分句并串行播放。
- 自动化测试需注入可控的语音合成 mock，避免依赖实际声卡。
