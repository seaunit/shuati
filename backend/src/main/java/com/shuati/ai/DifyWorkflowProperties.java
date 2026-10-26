package com.shuati.ai;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "shuati.dify")
public class DifyWorkflowProperties {

  private String baseUrl = "http://localhost:8501/v1";
  private Duration connectTimeout = Duration.ofSeconds(10);
  private Duration readTimeout = Duration.ofSeconds(175);
  private Workflows workflows = new Workflows();

  public String normalizedBaseUrl() {
    return baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
  }

  public String tokenFor(AiWorkflowKey key) {
    return switch (key) {
      case JUDGE -> workflows.getJudge();
      case EXPLAIN -> workflows.getExplain();
      case EXTRACT_TEXT -> workflows.getExtractText();
      case EXTRACT_IMAGE -> workflows.getExtractImage();
    };
  }

  public void validateTimeouts() {
    if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
      throw new IllegalStateException("Dify connect-timeout 必须大于 0");
    }
    if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalStateException("Dify read-timeout 必须大于 0");
    }
  }

  @Data
  public static class Workflows {
    private String judge;
    private String explain;
    private String extractText;
    private String extractImage;
  }
}
