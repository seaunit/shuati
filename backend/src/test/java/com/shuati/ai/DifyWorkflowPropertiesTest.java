package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DifyWorkflowPropertiesTest {

  @Test
  void normalizesBaseUrlAndResolvesEachWorkflowToken() {
    DifyWorkflowProperties properties = new DifyWorkflowProperties();
    properties.setBaseUrl("http://localhost:8501/v1///");
    properties.setConnectTimeout(Duration.ofSeconds(3));
    properties.setReadTimeout(Duration.ofSeconds(20));
    properties.getWorkflows().setJudge("judge-token");
    properties.getWorkflows().setExplain("explain-token");
    properties.getWorkflows().setAsk("ask-token");
    properties.getWorkflows().setExtractText("extract-text-token");
    properties.getWorkflows().setExtractImage("extract-image-token");

    assertThat(properties.normalizedBaseUrl()).isEqualTo("http://localhost:8501/v1");
    assertThat(properties.tokenFor(AiWorkflowKey.JUDGE)).isEqualTo("judge-token");
    assertThat(properties.tokenFor(AiWorkflowKey.EXPLAIN)).isEqualTo("explain-token");
    assertThat(properties.tokenFor(AiWorkflowKey.ASK)).isEqualTo("ask-token");
    assertThat(properties.tokenFor(AiWorkflowKey.EXTRACT_TEXT)).isEqualTo("extract-text-token");
    assertThat(properties.tokenFor(AiWorkflowKey.EXTRACT_IMAGE)).isEqualTo("extract-image-token");
  }

  @Test
  void rejectsNonPositiveTimeout() {
    DifyWorkflowProperties properties = new DifyWorkflowProperties();
    properties.setConnectTimeout(Duration.ZERO);

    org.assertj.core.api.Assertions.assertThatThrownBy(properties::validateTimeouts)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Dify connect-timeout");
  }
}
