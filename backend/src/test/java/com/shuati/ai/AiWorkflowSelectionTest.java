package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "shuati.ai.engine=dify",
    "shuati.dify.base-url=http://localhost:8501/v1",
    "shuati.dify.workflows.judge=test-judge",
    "shuati.dify.workflows.explain=test-explain",
    "shuati.dify.workflows.extract-text=test-extract-text",
    "shuati.dify.workflows.extract-image=test-extract-image"
})
class AiWorkflowSelectionTest {

  @Autowired AiWorkflowPort workflow;

  @Test
  void difyEngineSelectsOnlyDifyWorkflowAdapter() {
    assertThat(workflow).isInstanceOf(DifyWorkflowClient.class);
    assertThat(workflow.provider()).isEqualTo("DIFY");
  }
}
