# Data Model: 答案语音朗读

## SpeechSegment

- `id: string`：稳定段落 ID
- `text: string`：适合朗读的纯文本
- `kind: "text" | "code"`：正文或代码段
- `source: "verdict" | "answer" | "feedback" | "explanation" | "code"`：内容来源

## SpeechState

状态转移：

```text
idle -> playing -> paused -> playing
playing -> idle
paused -> idle
playing -> error
```

- `idle`：尚未播放或已停止
- `playing`：正在串行朗读
- `paused`：当前 utterance 暂停
- `unsupported`：浏览器或中文语音不可用
- `error`：播放出错

## SpeechPreference

- `rate: 0.75 | 1 | 1.25 | 1.5`
- `readCode: boolean`

保存位置：`sessionStorage`。

## Component Inputs

答案与解析内容由练习页组合成有序脚本；组件只负责播放与交互，不执行业务判定。
