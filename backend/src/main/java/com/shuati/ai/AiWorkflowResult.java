package com.shuati.ai;

public record AiWorkflowResult(
    String content,
    String provider,
    AiWorkflowKey workflowKey,
    String externalRunId,
    String model,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    long durationMs) {
}
