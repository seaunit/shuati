# Contract: 原题抽取行为

## Input

- 用户上传的截图、文档、网页文本或纯文本。
- API 与点数契约不变。

## Classification

- 资料含标题、题号、小问、输入输出、打印要求、请写出/实现/说明等：`QUESTION_PAPER`
- 没有现成问题，只有讲解或知识：`LEARNING_NOTES`

## QUESTION_PAPER Output

- 只输出原卷中的题目。
- 每个顶层题号或二选一候选项对应一个 item。
- 题内小问和约束保留在同一个 `content`。
- 无选项使用 `SHORT`，不得虚构选项。
- 表格使用 Markdown 表格，并写入所有依赖题目的题干。

## LEARNING_NOTES Output

- 保持原有 5-10 道生成题能力。
- 答案和解析必须来自资料，不得编造。
