# AnswerSpeechPlayer Contract

## Props

```ts
interface Props {
  contentKey: string | number;
  referenceAnswer?: string;
  feedback?: string;
  hitPoints?: string[];
  missedPoints?: string[];
  explanation?: string;
}
```

- 文本输入更新时，组件重建脚本。
- `contentKey` 变化时，立即停止并回到 `idle`。

## Controls

- 主按钮：`朗读` / `暂停` / `继续`
- 停止按钮：仅播放或暂停状态显示
- 语速菜单：`0.75x`、`1x`、`1.25x`、`1.5x`
- 音色菜单：设备存在多个中文音色时显示
- 代码开关：`朗读代码`

## Events and Side Effects

- 不请求后端。
- 不自动播放。
- 组件卸载、页面隐藏、题目变化时调用 `speechSynthesis.cancel()`。

## Accessibility

- 所有按钮有明确的 `aria-label`。
- 当前句子区域使用 `aria-live="polite"`。
- 状态文本通过 `role="status"` 暴露。

## Failure Behavior

- 无 `speechSynthesis`：显示“当前设备不支持语音朗读”，不显示播放按钮。
- 无中文语音：显示“设备未安装中文语音”。
- utterance 异常：停止播放并显示可重试状态。
