package com.shuati.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.common.ApiException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public class DifyWorkflowClient implements AiWorkflowPort {

  private static final String PROVIDER = "DIFY";

  private final DifyWorkflowProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public DifyWorkflowClient(
      DifyWorkflowProperties properties,
      ObjectMapper objectMapper,
      RestClient restClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
    properties.validateTimeouts();
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public AiWorkflowResult judge(String userId, AiWorkflowInputs.JudgeInput input) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("questionContent", value(input.questionContent()));
    inputs.put("keyPoints", value(input.keyPoints()));
    inputs.put("referenceAnswer", value(input.referenceAnswer()));
    inputs.put("userAnswer", value(input.userAnswer()));
    return run(AiWorkflowKey.JUDGE, userId, inputs, null, "result");
  }

  @Override
  public AiWorkflowResult explain(String userId, AiWorkflowInputs.ExplainInput input) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("questionContent", value(input.questionContent()));
    inputs.put("options", value(input.options()));
    inputs.put("referenceAnswer", value(input.referenceAnswer()));
    return run(AiWorkflowKey.EXPLAIN, userId, inputs, null, "explanation");
  }

  @Override
  public AiWorkflowResult ask(String userId, AiWorkflowInputs.AskInput input) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("questionContent", value(input.questionContent()));
    inputs.put("options", value(input.options()));
    inputs.put("referenceAnswer", value(input.referenceAnswer()));
    inputs.put("explanation", value(input.explanation()));
    inputs.put("conversation", value(input.conversation()));
    inputs.put("userQuestion", value(input.userQuestion()));
    return run(AiWorkflowKey.ASK, userId, inputs, null, "answer");
  }

  @Override
  public AiWorkflowResult extractText(String userId, AiWorkflowInputs.ExtractTextInput input) {
    return run(
        AiWorkflowKey.EXTRACT_TEXT,
        userId,
        Map.of("sourceText", value(input.sourceText())),
        null,
        "result");
  }

  @Override
  public AiWorkflowResult extractScreenshot(
      String userId, AiWorkflowInputs.ExtractScreenshotInput input) {
    String uploadFileId = uploadImage(userId, input.imageDataUrl());
    Map<String, Object> inputs = Map.of("instruction", value(input.instruction()));
    List<Map<String, Object>> files = List.of(Map.of(
        "type", "image",
        "transfer_method", "local_file",
        "upload_file_id", uploadFileId));
    return run(AiWorkflowKey.EXTRACT_IMAGE, userId, inputs, files, "result");
  }

  private AiWorkflowResult run(
      AiWorkflowKey workflowKey,
      String userId,
      Map<String, Object> inputs,
      List<Map<String, Object>> files,
      String outputKey) {
    String token = requireToken(workflowKey);
    long started = System.currentTimeMillis();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("inputs", inputs);
    if (files != null && !files.isEmpty()) {
      body.put("files", files);
    }
    body.put("response_mode", "blocking");
    body.put("user", "shuati:" + value(userId));

    JsonNode root;
    try {
      root = restClient.post()
          .uri(properties.normalizedBaseUrl() + "/workflows/run")
          .header("Authorization", "Bearer " + token)
          .header("X-Trace-Id", "shuati-" + Long.toUnsignedString(System.nanoTime()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
    } catch (ResourceAccessException e) {
      log.warn("Dify 工作流网络异常 workflow={}", workflowKey.key(), e);
      throw new ApiException(502, "AI 编排服务响应超时或不可用");
    } catch (RestClientResponseException e) {
      log.warn("Dify 工作流 HTTP 异常 workflow={} status={}",
          workflowKey.key(), e.getStatusCode().value());
      throw new ApiException(502, "AI 编排服务不可用");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Dify 工作流调用异常 workflow={}", workflowKey.key(), e);
      throw new ApiException(502, "AI 编排服务调用失败");
    }
    if (root == null || root.isNull()) {
      throw new ApiException(502, "AI 编排服务返回内容为空");
    }
    JsonNode data = root.path("data");
    String status = data.path("status").asText("");
    if (!"succeeded".equalsIgnoreCase(status)) {
      throw new ApiException(502, "AI 工作流执行失败");
    }
    JsonNode output = data.path("outputs").path(outputKey);
    if (output.isMissingNode() || output.isNull()
        || (output.isTextual() && output.asText().isBlank())) {
      throw new ApiException(502, "AI 工作流返回格式不正确");
    }
    String content;
    try {
      content = output.isTextual() ? output.asText().trim() : objectMapper.writeValueAsString(output);
      content = stripReasoning(content);
    } catch (Exception e) {
      throw new ApiException(502, "AI 工作流返回格式不正确");
    }
    String runId = root.path("workflow_run_id").asText(data.path("id").asText(""));
    int totalTokens = data.path("total_tokens").asInt(0);
    long duration = data.has("elapsed_time")
        ? Math.max(0, Math.round(data.path("elapsed_time").asDouble() * 1000))
        : System.currentTimeMillis() - started;
    return new AiWorkflowResult(
        content,
        PROVIDER,
        workflowKey,
        runId.isBlank() ? null : runId,
        "dify:" + workflowKey.key(),
        0,
        0,
        Math.max(0, totalTokens),
        duration);
  }

  private String uploadImage(String userId, String dataUrl) {
    String token = requireToken(AiWorkflowKey.EXTRACT_IMAGE);
    ImagePayload image = parseImage(dataUrl);
    MultipartBodyBuilder multipart = new MultipartBodyBuilder();
    multipart.part("file", new ByteArrayResource(image.bytes()) {
      @Override
      public String getFilename() {
        return "screenshot." + image.extension();
      }
    }).contentType(MediaType.parseMediaType(image.mimeType()));
    multipart.part("user", "shuati:" + value(userId));

    JsonNode root;
    try {
      root = restClient.post()
          .uri(properties.normalizedBaseUrl() + "/files/upload")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(multipart.build())
          .retrieve()
          .body(JsonNode.class);
    } catch (ResourceAccessException e) {
      throw new ApiException(502, "AI 编排文件上传超时或不可用");
    } catch (RestClientResponseException e) {
      throw new ApiException(502, "AI 编排文件上传失败");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "AI 编排文件上传失败");
    }
    String fileId = root == null ? "" : root.path("id").asText("");
    if (fileId.isBlank()) {
      throw new ApiException(502, "AI 编排文件上传返回格式不正确");
    }
    return fileId;
  }

  private ImagePayload parseImage(String dataUrl) {
    int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
    if (comma < 0 || !dataUrl.startsWith("data:image/")
        || !dataUrl.substring(0, comma).contains(";base64")) {
      throw new ApiException(400, "图片格式不正确");
    }
    String header = dataUrl.substring(5, comma);
    String mime = header.split(";")[0];
    String extension = switch (mime) {
      case "image/jpeg" -> "jpg";
      case "image/webp" -> "webp";
      default -> "png";
    };
    try {
      byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
      return new ImagePayload(bytes, mime, extension);
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "图片格式不正确");
    }
  }

  private String requireToken(AiWorkflowKey key) {
    String token = properties.tokenFor(key);
    if (!StringUtils.hasText(token)) {
      throw new ApiException(502, "AI 编排服务未配置");
    }
    return token.trim();
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private String stripReasoning(String content) {
    return content.replaceFirst(
        "(?is)^\\s*<think(?:ing)?>.*?</think(?:ing)?>\\s*",
        "").trim();
  }

  private record ImagePayload(byte[] bytes, String mimeType, String extension) {
  }
}
