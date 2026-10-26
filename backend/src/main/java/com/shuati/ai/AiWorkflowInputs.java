package com.shuati.ai;

public final class AiWorkflowInputs {

  private AiWorkflowInputs() {
  }

  public record JudgeInput(
      String questionContent,
      String keyPoints,
      String referenceAnswer,
      String userAnswer) {
  }

  public record ExplainInput(
      String questionContent,
      String options,
      String referenceAnswer) {
  }

  public record ExtractTextInput(String sourceText) {
  }

  public record ExtractScreenshotInput(String imageDataUrl, String instruction) {
  }
}
