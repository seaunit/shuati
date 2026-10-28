# Research: 判断题题型

## Decision: 复用 `question.options` 和 `question.answer`

**Rationale**: 现有练习提交、进度、错题本和 Word 导出都围绕选项列表与字符串答案工作。固定选项 `T=正确`、`F=错误` 可以最小化改动，并继续复用 `/api/practice/submit-choice`。

**Alternatives considered**:

- 新增布尔列：需要改 API、导出和客户端契约，成本更高。
- 将答案存成中文“正确/错误”：与现有单字符选项比较和错题记录兼容性较差。

## Decision: 服务端统一归一化

**Rationale**: 导入模型、后台表单和题库维护都可能提交不同表达。服务端接受 `T/F`、`TRUE/FALSE`、`正确/错误`、`对/错`、`√/×`，统一保存为 `T/F`，防止前端或历史数据绕过校验。

**Alternatives considered**:

- 只在前端限制：无法保护直接 API 调用和 AI 导入数据。
- 保存原始表达：练习判分和导出会形成多套口径。

## Decision: 不在判断题中使用 AI 判分

**Rationale**: 正确答案只有两个取值，本地比较即可，必须避免无意义的 AI 成本和延迟。

**Alternatives considered**:

- AI 判分：既慢又无法提高确定性，还会错误扣点。
