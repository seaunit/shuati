# Contract: Backend AI Workflow Port

## Purpose

业务层只通过以下语义方法与 AI 交互，不传递模型协议、system prompt 或供应商字段。

## Interface

```text
judge(JudgeInput) -> AiWorkflowResult
explain(ExplainInput) -> AiWorkflowResult
extractText(ExtractTextInput) -> AiWorkflowResult
extractScreenshot(ExtractScreenshotInput) -> AiWorkflowResult
```

## JudgeInput

- `questionContent`: string，必填
- `keyPoints`: string，可空
- `referenceAnswer`: string，必填
- `userAnswer`: string，必填

`content` 输出必须是 JSON 字符串：

```json
{
  "verdict": "CORRECT | PARTIAL | WRONG",
  "score": 0,
  "hit_points": ["..."],
  "missed_points": ["..."],
  "feedback": "Markdown"
}
```

## ExplainInput

- `questionContent`: string，必填
- `options`: string，可空
- `referenceAnswer`: string，必填

`content` 输出必须是 Markdown 字符串。

## ExtractTextInput

- `sourceText`: string，必填

`content` 输出必须是 JSON 字符串：

```json
{
  "questions": [
    {
      "unit": "未分类",
      "type": "SINGLE | MULTI | SHORT",
      "content": "题干",
      "options": [{"key": "A", "text": "选项"}],
      "answer": "A",
      "key_points": ["..."],
      "difficulty": "EASY | MEDIUM | HARD",
      "explanation": "解析"
    }
  ]
}
```

## ExtractScreenshotInput

- `imageDataUrl`: data URL，必填
- `instruction`: string，必填

输出结构和 `ExtractTextInput` 相同。

## AiWorkflowResult

- `content`: string，AI 主体输出
- `provider`: `DIFY` 或 `DIRECT`
- `workflowKey`: 工作流语义键
- `externalRunId`: Dify 运行 ID，可空
- `model`: 模型或工作流标识
- `promptTokens`: integer，可为 0
- `completionTokens`: integer，可为 0
- `totalTokens`: integer，可为 0
- `durationMs`: long

## Error Contract

- 配置缺失：`502 AI 编排服务未配置`
- 连接失败：`502 AI 编排服务不可用`
- 超时：`502 AI 编排服务响应超时`
- 工作流失败：`502 AI 工作流执行失败`
- 输出无效：`502 AI 工作流返回格式不正确`

后端必须在抛出上述错误前完成退款和失败调用日志。
