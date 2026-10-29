package com.shuati.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "shuati.ai.engine",
    havingValue = "direct",
    matchIfMissing = true)
public class DirectAiWorkflowAdapter implements AiWorkflowPort {

  private final AiClient ai;

  @Override
  public String provider() {
    return "DIRECT";
  }

  @Override
  public AiWorkflowResult judge(String userId, AiWorkflowInputs.JudgeInput input) {
    long started = System.currentTimeMillis();
    AiClient.AiResult result = ai.chat(
        "JUDGE",
        PromptTemplates.ESSAY_SYSTEM,
        PromptTemplates.essayUser(
            input.questionContent(), input.keyPoints(), input.referenceAnswer(), input.userAnswer()),
        true);
    return result(AiWorkflowKey.JUDGE, result, started);
  }

  @Override
  public AiWorkflowResult explain(String userId, AiWorkflowInputs.ExplainInput input) {
    long started = System.currentTimeMillis();
    AiClient.AiResult result = ai.chat(
        "EXPLAIN",
        PromptTemplates.EXPLAIN_SYSTEM,
        PromptTemplates.explainUser(
            input.questionContent(), input.options(), input.referenceAnswer()),
        false);
    return result(AiWorkflowKey.EXPLAIN, result, started);
  }

  @Override
  public AiWorkflowResult ask(String userId, AiWorkflowInputs.AskInput input) {
    long started = System.currentTimeMillis();
    AiClient.AiResult result = ai.chat(
        "ASK",
        PromptTemplates.ASK_SYSTEM,
        PromptTemplates.askUser(
            input.questionContent(),
            input.options(),
            input.referenceAnswer(),
            input.explanation(),
            input.conversation(),
            input.userQuestion()),
        false);
    return result(AiWorkflowKey.ASK, result, started);
  }

  @Override
  public AiWorkflowResult extractText(String userId, AiWorkflowInputs.ExtractTextInput input) {
    long started = System.currentTimeMillis();
    AiClient.AiResult result =
        ai.chat("EXTRACT", PromptTemplates.EXTRACT_SYSTEM, input.sourceText(), true);
    return result(AiWorkflowKey.EXTRACT_TEXT, result, started);
  }

  @Override
  public AiWorkflowResult extractScreenshot(
      String userId, AiWorkflowInputs.ExtractScreenshotInput input) {
    long started = System.currentTimeMillis();
    AiClient.AiResult result = ai.chatWithImage(
        "EXTRACT",
        PromptTemplates.EXTRACT_SYSTEM,
        input.instruction(),
        input.imageDataUrl(),
        true);
    return result(AiWorkflowKey.EXTRACT_IMAGE, result, started);
  }

  private AiWorkflowResult result(
      AiWorkflowKey key, AiClient.AiResult result, long started) {
    return new AiWorkflowResult(
        result.content(),
        "DIRECT",
        key,
        null,
        result.model(),
        result.promptTokens(),
        result.completionTokens(),
        result.totalTokens(),
        System.currentTimeMillis() - started);
  }
}
