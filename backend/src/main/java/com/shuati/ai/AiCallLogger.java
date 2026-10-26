package com.shuati.ai;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiCallLogger {

  private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+\\S+");
  private static final Pattern APP_KEY = Pattern.compile("\\bapp-[A-Za-z0-9_-]+");
  private static final Pattern PROVIDER_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]+");
  private static final int MAX_ERROR_LENGTH = 2000;

  private final JdbcTemplate jdbc;

  public void success(String userId, String purpose, Long questionId, AiWorkflowResult result) {
    insert(
        userId,
        purpose,
        questionId,
        result.provider(),
        result.workflowKey() == null ? null : result.workflowKey().key(),
        result.externalRunId(),
        result.model(),
        result.promptTokens(),
        result.completionTokens(),
        result.totalTokens(),
        true,
        null,
        result.durationMs());
  }

  public void failure(
      String userId,
      String purpose,
      Long questionId,
      String provider,
      AiWorkflowKey workflowKey,
      String externalRunId,
      String error,
      long durationMs) {
    insert(
        userId,
        purpose,
        questionId,
        provider,
        workflowKey == null ? null : workflowKey.key(),
        externalRunId,
        workflowKey == null ? "unknown" : "dify:" + workflowKey.key(),
        0,
        0,
        0,
        false,
        sanitize(error),
        durationMs);
  }

  String sanitize(String error) {
    if (error == null || error.isBlank()) {
      return "未知错误";
    }
    String value = BEARER.matcher(error).replaceAll("Bearer ***");
    value = APP_KEY.matcher(value).replaceAll("app-***");
    value = PROVIDER_KEY.matcher(value).replaceAll("sk-***");
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }

  private void insert(
      String userId,
      String purpose,
      Long questionId,
      String provider,
      String workflowKey,
      String externalRunId,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      boolean success,
      String error,
      long durationMs) {
    try {
      jdbc.update("""
          insert into ai_call_log
            (user_id, purpose, provider, workflow_key, external_run_id, model, question_id,
             prompt_tokens, completion_tokens, total_tokens, success, error, duration_ms)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          userId,
          purpose,
          provider == null || provider.isBlank() ? "DIRECT" : provider,
          workflowKey,
          externalRunId,
          model == null || model.isBlank() ? "unknown" : model,
          questionId,
          Math.max(0, promptTokens),
          Math.max(0, completionTokens),
          Math.max(0, totalTokens),
          success,
          error,
          Math.max(0, durationMs));
    } catch (Exception ignored) {
      // 审计失败不影响用户主流程。
    }
  }
}
