package com.shuati.ai;

public enum AiWorkflowKey {
  JUDGE("judge", "JUDGE"),
  EXPLAIN("explain", "EXPLAIN"),
  EXTRACT_TEXT("extract-text", "EXTRACT"),
  EXTRACT_IMAGE("extract-image", "EXTRACT");

  private final String key;
  private final String purpose;

  AiWorkflowKey(String key, String purpose) {
    this.key = key;
    this.purpose = purpose;
  }

  public String key() {
    return key;
  }

  public String purpose() {
    return purpose;
  }

  public static AiWorkflowKey fromKey(String value) {
    for (AiWorkflowKey key : values()) {
      if (key.key.equals(value)) {
        return key;
      }
    }
    throw new IllegalArgumentException("未知 AI 工作流：" + value);
  }
}
