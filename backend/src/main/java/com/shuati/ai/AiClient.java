package com.shuati.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.common.ApiException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiClient {

  private final JdbcTemplate jdbc;
  private final AesCrypto aes;
  private final ObjectMapper objectMapper;
  private final Environment environment;

  private RestClient restClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(20));
    factory.setReadTimeout(Duration.ofSeconds(175));
    return RestClient.builder().requestFactory(factory).build();
  }

  public AiResult chat(String purpose, String system, String user, boolean jsonMode) {
    AiEndpoint endpoint = resolve(purpose);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", endpoint.model());
    body.put("messages", List.of(
        Map.of("role", "system", "content", system),
        Map.of("role", "user", "content", user)));
    body.put("temperature", 0.3);
    if (jsonMode) {
      body.put("response_format", Map.of("type", "json_object"));
    }

    Map<?, ?> response;
    try {
      response = restClient().post()
          .uri(endpoint.baseUrl() + "/chat/completions")
          .header("Authorization", "Bearer " + endpoint.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
    } catch (Exception e) {
      throw new ApiException(502, "AI 请求失败：" + e.getMessage());
    }
    if (response == null) {
      throw new ApiException(502, "AI 返回内容为空");
    }

    String content = "";
    Object choicesValue = response.get("choices");
    if (choicesValue instanceof List<?> choices && !choices.isEmpty()
        && choices.get(0) instanceof Map<?, ?> choice
        && choice.get("message") instanceof Map<?, ?> message) {
      Object contentValue = message.get("content");
      content = contentValue == null ? "" : String.valueOf(contentValue).trim();
    }
    if (content.isEmpty()) {
      throw new ApiException(502, "AI 返回内容为空");
    }
    content = jsonMode ? stripCodeFence(content) : content;

    int promptTokens = 0;
    int completionTokens = 0;
    int totalTokens = 0;
    if (response.get("usage") instanceof Map<?, ?> usage) {
      promptTokens = intValue(usage.get("prompt_tokens"));
      completionTokens = intValue(usage.get("completion_tokens"));
      totalTokens = intValue(usage.get("total_tokens"));
    }
    return new AiResult(content, endpoint.model(), promptTokens, completionTokens, totalTokens);
  }

  public void logUsage(
      String userId, String purpose, String model, Long questionId,
      AiResult result, String error, long durationMs) {
    try {
      jdbc.update("""
          insert into ai_call_log
            (user_id, purpose, model, question_id, prompt_tokens, completion_tokens,
             total_tokens, success, error, duration_ms)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, userId, purpose, model, questionId,
          result == null ? 0 : result.promptTokens(),
          result == null ? 0 : result.completionTokens(),
          result == null ? 0 : result.totalTokens(),
          error == null ? 1 : 0, error, durationMs);
    } catch (Exception ignored) {
      // 日志失败不影响主流程
    }
  }

  public AiEndpoint resolve(String purpose) {
    // 单条配置模型：不再按 status 过滤，库里这一条就是当前生效配置
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select base_url, api_key_encrypted, model, purpose
          from ai_config
         where purpose in (?, 'BOTH')
         order by id asc
         limit 20
        """, purpose);
    if (!rows.isEmpty()) {
      Map<String, Object> exact = rows.stream()
          .filter(row -> purpose.equals(row.get("purpose")))
          .findFirst().orElse(rows.get(0));
      return new AiEndpoint(
          String.valueOf(exact.get("base_url")).replaceAll("/+$", ""),
          aes.decrypt(String.valueOf(exact.get("api_key_encrypted"))),
          String.valueOf(exact.get("model")));
    }
    String key = environment.getProperty("DEEPSEEK_API_KEY");
    if (key == null || key.isBlank()) {
      throw new ApiException(500, "未配置可用的 AI 接入（请在后台填写 DeepSeek API Key）");
    }
    String baseUrl = environment.getProperty("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
    String model = environment.getProperty("DEEPSEEK_MODEL", "deepseek-flash");
    return new AiEndpoint(baseUrl.replaceAll("/+$", ""), key, model);
  }

  public String stripCodeFence(String text) {
    String value = text.trim();
    if (value.startsWith("```")) {
      int newline = value.indexOf('\n');
      if (newline > 0) {
        value = value.substring(newline + 1);
      }
      if (value.endsWith("```")) {
        value = value.substring(0, value.length() - 3);
      }
    }
    return value.trim();
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  private int intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }

  public record AiEndpoint(String baseUrl, String apiKey, String model) {
  }

  public record AiResult(
      String content, String model, int promptTokens, int completionTokens, int totalTokens) {
  }
}
