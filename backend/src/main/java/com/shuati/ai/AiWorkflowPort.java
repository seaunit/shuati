package com.shuati.ai;

public interface AiWorkflowPort {

  String provider();

  AiWorkflowResult judge(String userId, AiWorkflowInputs.JudgeInput input);

  AiWorkflowResult explain(String userId, AiWorkflowInputs.ExplainInput input);

  AiWorkflowResult ask(String userId, AiWorkflowInputs.AskInput input);

  AiWorkflowResult extractText(String userId, AiWorkflowInputs.ExtractTextInput input);

  AiWorkflowResult extractScreenshot(
      String userId, AiWorkflowInputs.ExtractScreenshotInput input);
}
