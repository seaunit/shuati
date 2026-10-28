# Data Model: 判断题题型

## Question

新增题型：

```text
type = TRUE_FALSE
options = [
  {"key":"T","text":"正确"},
  {"key":"F","text":"错误"}
]
answer = "T" | "F"
```

约束：

- `type` 只允许 `SINGLE / MULTI / TRUE_FALSE / SHORT`。
- 判断题必须恰好有 `T/F` 两项；保存前由服务端重建固定选项。
- 答案只允许 `T` 或 `F`，保存前归一化。
- `key_points` 对判断题不使用，保存为 `null`。
- 判断题仍属于 `ON/OFF` 状态、单元归属和现有题库权限范围。

## Answer Normalization

| 输入 | 标准值 |
| --- | --- |
| `T`, `TRUE`, `正确`, `对`, `是`, `√`, `✓`, `☑` | `T` |
| `F`, `FALSE`, `错误`, `错`, `否`, `×`, `✕`, `✗`, `☒` | `F` |

其他值或同时出现两个答案时拒绝保存。

## PracticeRecord

不新增字段。判断题继续写入：

- `user_answer`: `T` 或 `F`
- `judge_source`: `LOCAL`
- `verdict`: `CORRECT` / `WRONG`
- `score`: `10` / `0`

## ImportItem

判断题导入结果在入库前归一化：

- `type`: `TRUE_FALSE`
- `options`: 固定 `T/F` 列表
- `answer`: 归一化为 `T/F`

## UI Contract

- 题型名称：`判断题`
- 答案名称：`正确` / `错误`
- 编辑表单只允许单选一个答案，不显示自定义选项输入。
- 练习页只显示两个按钮：`正确`、`错误`。
