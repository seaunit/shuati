package com.shuati.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.common.ApiException;
import java.time.Duration;
import java.util.ArrayList;
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

  public static final String PROTOCOL_OPENAI = "OPENAI";
  public static final String PROTOCOL_ANTHROPIC = "ANTHROPIC";

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
    return isAnthropic(endpoint.protocol())
        ? chatAnthropic(endpoint, system, user, jsonMode)
        : chatOpenAi(endpoint, system, user, jsonMode);
  }

  /** OpenAI 兼容：POST {base}/chat/completions */
  private AiResult chatOpenAi(AiEndpoint endpoint, String system, String user, boolean jsonMode) {
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
    if (response.get("usage") instanceof Map<?, ?> usage) {
      promptTokens = intValue(usage.get("prompt_tokens"));
      completionTokens = intValue(usage.get("completion_tokens"));
    }
    return new AiResult(content, endpoint.model(), promptTokens, completionTokens,
        promptTokens + completionTokens);
  }

  /** Anthropic 兼容：POST {base}/v1/messages，头部用 x-api-key */
  private AiResult chatAnthropic(AiEndpoint endpoint, String system, String user, boolean jsonMode) {
    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of("role", "user", "content", user));
    if (jsonMode) {
      // 用 assistant 前缀强制模型从 JSON 开始输出，避免多余解释
      messages.add(Map.of("role", "assistant", "content", "{"));
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", endpoint.model());
    body.put("max_tokens", 4096);
    body.put("system", system);
    body.put("messages", messages);

    Map<?, ?> response;
    try {
      response = restClient().post()
          .uri(endpoint.baseUrl() + "/v1/messages")
          .header("x-api-key", endpoint.apiKey())
          .header("anthropic-version", "2023-06-01")
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

    StringBuilder content = new StringBuilder();
    Object blocksValue = response.get("content");
    if (blocksValue instanceof List<?> blocks) {
      for (Object block : blocks) {
        if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
          content.append(map.get("text") == null ? "" : String.valueOf(map.get("text")));
        }
      }
    }
    String text = content.toString().trim();
    if (jsonMode) {
      text = "{" + text;
    }
    if (text.isEmpty()) {
      throw new ApiException(502, "AI 返回内容为空");
    }
    text = jsonMode ? stripCodeFence(text) : text;

    int inputTokens = 0;
    int outputTokens = 0;
    if (response.get("usage") instanceof Map<?, ?> usage) {
      inputTokens = intValue(usage.get("input_tokens"));
      outputTokens = intValue(usage.get("output_tokens"));
    }
    return new AiResult(text, endpoint.model(), inputTokens, outputTokens,
        inputTokens + outputTokens);
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
        select base_url, api_key_encrypted, model, protocol, purpose
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
          String.valueOf(exact.get("model")),
          normalizeProtocol(exact.get("protocol") == null
              ? null : String.valueOf(exact.get("protocol"))));
    }
    String key = environment.getProperty("DEEPSEEK_API_KEY");
    if (key == null || key.isBlank()) {
      throw new ApiException(500, "未配置可用的 AI 接入（请在后台填写 AI 配置）");
    }
    String baseUrl = environment.getProperty("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
    String model = environment.getProperty("DEEPSEEK_MODEL", "deepseek-flash");
    return new AiEndpoint(baseUrl.replaceAll("/+$", ""), key, model, PROTOCOL_OPENAI);
  }

  public static boolean isAnthropic(String protocol) {
    return PROTOCOL_ANTHROPIC.equalsIgnoreCase(protocol);
  }

  public static String normalizeProtocol(String protocol) {
    return isAnthropic(protocol) ? PROTOCOL_ANTHROPIC : PROTOCOL_OPENAI;
  }

  public static String defaultBaseUrl(String protocol) {
    return isAnthropic(protocol)
        ? "https://api.deepseek.com/anthropic"
        : "https://api.deepseek.com";
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

  public record AiEndpoint(String baseUrl, String apiKey, String model, String protocol) {
  }

  public record AiResult(
      String content, String model, int promptTokens, int completionTokens, int totalTokens) {
  }
}
