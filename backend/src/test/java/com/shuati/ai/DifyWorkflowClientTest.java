package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shuati.common.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DifyWorkflowClientTest {

  private static final String BASE_URL = "http://dify.test/v1";
  private static final String PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DifyWorkflowProperties properties;
  private RestClient.Builder builder;
  private MockRestServiceServer server;
  private DifyWorkflowClient client;

  @BeforeEach
  void setUp() {
    properties = new DifyWorkflowProperties();
    properties.setBaseUrl(BASE_URL);
    properties.setConnectTimeout(Duration.ofSeconds(2));
    properties.setReadTimeout(Duration.ofSeconds(10));
    properties.getWorkflows().setJudge("judge-token");
    properties.getWorkflows().setExplain("explain-token");
    properties.getWorkflows().setAsk("ask-token");
    properties.getWorkflows().setExtractText("extract-text-token");
    properties.getWorkflows().setExtractImage("extract-image-token");

    builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new DifyWorkflowClient(properties, objectMapper, builder.build());
  }

  @Test
  void judgeUsesConfiguredWorkflowAndReturnsRunMetadata() throws Exception {
    ObjectNode judgeOutput = objectMapper.createObjectNode()
        .put("verdict", "CORRECT")
        .put("score", 9)
        .put("feedback", "回答正确");
    judgeOutput.putArray("hit_points").add("命中");
    judgeOutput.putArray("missed_points");
    String output = "<think>内部推理</think>" + objectMapper.writeValueAsString(judgeOutput);
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer judge-token"))
        .andExpect(jsonPath("$.inputs.questionContent").value("解释 HashMap"))
        .andExpect(jsonPath("$.response_mode").value("blocking"))
        .andExpect(jsonPath("$.user").value("shuati:user-1"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-judge",
              "task_id": "task-judge",
              "data": {
                "id": "run-judge",
                "workflow_id": "workflow-1",
                "status": "succeeded",
                "outputs": {"result": %s},
                "error": null,
                "elapsed_time": 1.25,
                "total_tokens": 42
              }
            }
            """.formatted(objectMapper.writeValueAsString(output)), MediaType.APPLICATION_JSON));

    AiWorkflowResult result = client.judge(
        "user-1", new AiWorkflowInputs.JudgeInput("解释 HashMap", "扩容", "参考答案", "我的答案"));

    assertThat(result.content()).contains("\"verdict\":\"CORRECT\"");
    assertThat(result.content()).doesNotContain("<think>").doesNotContain("内部推理");
    assertThat(result.provider()).isEqualTo("DIFY");
    assertThat(result.workflowKey()).isEqualTo(AiWorkflowKey.JUDGE);
    assertThat(result.externalRunId()).isEqualTo("run-judge");
    assertThat(result.totalTokens()).isEqualTo(42);
  }

  @Test
  void explainReturnsMarkdownOutput() {
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andExpect(header("Authorization", "Bearer explain-token"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-explain",
              "data": {
                "status": "succeeded",
                "outputs": {"explanation": "<think>内部推理</think>正确答案是 C。\\n\\n```java\\nclass Demo {}\\n```"},
                "total_tokens": 12
              }
            }
            """, MediaType.APPLICATION_JSON));

    AiWorkflowResult result = client.explain(
        "user-1", new AiWorkflowInputs.ExplainInput("题目", "[]", "C"));

    assertThat(result.content()).contains("正确答案是 C");
    assertThat(result.content()).doesNotContain("<think>");
    assertThat(result.workflowKey()).isEqualTo(AiWorkflowKey.EXPLAIN);
  }

  @Test
  void askReturnsMarkdownAnswerWithQuestionContext() {
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andExpect(header("Authorization", "Bearer ask-token"))
        .andExpect(jsonPath("$.inputs.questionContent").value("MySQL 主从延迟如何排查"))
        .andExpect(jsonPath("$.inputs.referenceAnswer").value("B"))
        .andExpect(jsonPath("$.inputs.explanation").value("已有解析"))
        .andExpect(jsonPath("$.inputs.conversation").value("[{\"role\":\"user\",\"content\":\"继续\"}]"))
        .andExpect(jsonPath("$.inputs.userQuestion").value("半同步复制有什么作用？"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-ask",
              "data": {
                "status": "succeeded",
                "outputs": {"answer": "<think>内部推理</think>半同步复制要求至少一个从库确认。"},
                "total_tokens": 18
              }
            }
            """, MediaType.APPLICATION_JSON));

    AiWorkflowResult result = client.ask(
        "user-1",
        new AiWorkflowInputs.AskInput(
            "MySQL 主从延迟如何排查",
            "[]",
            "B",
            "已有解析",
            "[{\"role\":\"user\",\"content\":\"继续\"}]",
            "半同步复制有什么作用？"));

    assertThat(result.content()).isEqualTo("半同步复制要求至少一个从库确认。");
    assertThat(result.workflowKey()).isEqualTo(AiWorkflowKey.ASK);
  }

  @Test
  void extractTextReturnsQuestionJson() {
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andExpect(header("Authorization", "Bearer extract-text-token"))
        .andExpect(jsonPath("$.inputs.sourceText").value("笔记正文"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-extract",
              "data": {
                "status": "succeeded",
                "outputs": {"result": "{\\"questions\\":[]}"},
                "total_tokens": 8
              }
            }
            """, MediaType.APPLICATION_JSON));

    AiWorkflowResult result = client.extractText(
        "user-1", new AiWorkflowInputs.ExtractTextInput("笔记正文"));

    assertThat(result.content()).isEqualTo("{\"questions\":[]}");
    assertThat(result.workflowKey()).isEqualTo(AiWorkflowKey.EXTRACT_TEXT);
  }

  @Test
  void extractScreenshotUploadsFileThenRunsWorkflow() {
    server.expect(once(), requestTo(BASE_URL + "/files/upload"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer extract-image-token"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
        .andRespond(withStatus(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"id\":\"upload-1\",\"name\":\"screenshot.png\",\"size\":68}"));
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andExpect(header("Authorization", "Bearer extract-image-token"))
        .andExpect(jsonPath("$.files[0].type").value("image"))
        .andExpect(jsonPath("$.files[0].transfer_method").value("local_file"))
        .andExpect(jsonPath("$.files[0].upload_file_id").value("upload-1"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-image",
              "data": {
                "status": "succeeded",
                "outputs": {"result": "{\\"questions\\":[{\\"content\\":\\"题目\\"}]}"},
                "total_tokens": 20
              }
            }
            """, MediaType.APPLICATION_JSON));

    AiWorkflowResult result = client.extractScreenshot(
        "user-1", new AiWorkflowInputs.ExtractScreenshotInput(PNG, "识别题目"));

    assertThat(result.content()).contains("题目");
    assertThat(result.workflowKey()).isEqualTo(AiWorkflowKey.EXTRACT_IMAGE);
    server.verify();
  }

  @Test
  void failedWorkflowBecomesSanitizedBadGateway() {
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andRespond(withSuccess("""
            {
              "workflow_run_id": "run-failed",
              "data": {
                "status": "failed",
                "error": "Bearer app-secret provider failed",
                "outputs": {}
              }
            }
            """, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.explain(
        "user-1", new AiWorkflowInputs.ExplainInput("题目", "[]", "A")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("AI 工作流执行失败")
        .hasMessageNotContaining("app-secret");
  }

  @Test
  void invalidTokenBecomesBadGatewayWithoutLeakingResponseBody() {
    server.expect(once(), requestTo(BASE_URL + "/workflows/run"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"unauthorized\",\"message\":\"bad token secret-value\"}"));

    assertThatThrownBy(() -> client.judge(
        "user-1", new AiWorkflowInputs.JudgeInput("题目", null, "答案", "回答")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("AI 编排服务不可用")
        .hasMessageNotContaining("secret-value");
  }
}
