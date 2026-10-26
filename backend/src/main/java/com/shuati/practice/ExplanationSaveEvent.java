package com.shuati.practice;

import com.shuati.ai.AiWorkflowKey;

/**
 * 解析结果落库事件。
 *
 * <p>AI 解析生成完之后，结果先返回给用户，同时投递到 MQ 由消费者异步写库，
 * 用户不必等落库完成。字段带全是为了让消费者一次写完
 * {@code question.explanation} 与 {@code ai_call_log}，不需要再查库。
 */
public record ExplanationSaveEvent(
    long questionId,
    String userId,
    String explanation,
    String provider,
    AiWorkflowKey workflowKey,
    String externalRunId,
    String model,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    long durationMs) {
}
