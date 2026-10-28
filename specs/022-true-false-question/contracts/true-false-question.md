# True/False Question Contract

## Existing API Shapes

不新增接口。题目列表、练习提交、题库维护和后台管理继续使用现有 `/api/**` 结构。

### Question Payload

```json
{
  "type": "TRUE_FALSE",
  "content": "Spring 的 Bean 默认是单例。",
  "options": [
    { "key": "T", "text": "正确" },
    { "key": "F", "text": "错误" }
  ],
  "answer": "T"
}
```

### Submit Answer

```json
{
  "questionId": 123,
  "selected": ["T"]
}
```

### Submit Response

```json
{
  "recordId": 456,
  "verdict": "CORRECT",
  "score": 10,
  "correctAnswer": "T"
}
```

## Validation

- 后端收到 `TRUE_FALSE` 时忽略客户端自定义选项，统一重建 `T/F` 两项。
- 答案接受常见正误表达，但最终保存与返回 `T/F`。
- 空答案、双答案或未知答案返回 400。
- 判断题不得进入 `/api/practice/submit-essay` AI 判分链路。

## UI Behavior

- 题型标签：`判断题`
- 按钮文案：`正确`、`错误`
- 答对：10 分，正确项绿色
- 答错：0 分，所选项红色，正确项绿色
- 背题模式：正确项高亮，答案文本显示中文
- 移动端：与网页端共用同一组件与接口
