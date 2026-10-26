package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@EnabledIfEnvironmentVariable(named = "DIFY_LIVE_TEST", matches = "true")
class DifyLiveSmokeTest {

  @Test
  void explainWorkflowRunsThroughRealDifyClient() {
    DifyWorkflowProperties properties = new DifyWorkflowProperties();
    properties.setBaseUrl(System.getenv().getOrDefault(
        "DIFY_BASE_URL", "http://[::1]:8501/v1"));
    properties.setConnectTimeout(Duration.ofSeconds(10));
    properties.setReadTimeout(Duration.ofSeconds(175));
    properties.getWorkflows().setExplain(System.getenv("DIFY_WORKFLOW_EXPLAIN_TOKEN"));

    DifyWorkflowClient client = new DifyWorkflowConfiguration().difyWorkflowClient(
        properties, RestClient.builder(), new ObjectMapper());

    AiWorkflowResult result = client.explain(
        "live-smoke",
        new AiWorkflowInputs.ExplainInput(
            "Spring Boot 中 @Component 的作用是什么？",
            "[{\"key\":\"A\",\"text\":\"声明 Bean\"}]",
            "A"));

    assertThat(result.content()).isNotBlank();
    assertThat(result.externalRunId()).isNotBlank();
  }

  @Test
  void serializedJsonBodyReachesDifyWithoutConnectionReset() throws Exception {
    String baseUrl = System.getenv().getOrDefault("DIFY_BASE_URL", "http://[::1]:8501/v1");
    String token = System.getenv("DIFY_WORKFLOW_EXPLAIN_TOKEN");
    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, Object> body = Map.of(
        "inputs", Map.of(
            "questionContent", "Spring Boot 中 @Component 的作用是什么？",
            "options", "[{\"key\":\"A\",\"text\":\"声明 Bean\"}]",
            "referenceAnswer", "A"),
        "response_mode", "blocking",
        "user", "shuati:live-raw-json");
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(10));
    requestFactory.setReadTimeout(Duration.ofSeconds(175));
    String response = RestClient.builder().requestFactory(requestFactory).build()
        .post()
        .uri(baseUrl + "/workflows/run")
        .header("Authorization", "Bearer " + token)
        .header("X-Trace-Id", "shuati-live-raw")
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(objectMapper.writeValueAsString(body))
        .retrieve()
        .body(String.class);

    JsonNode root = objectMapper.readTree(response);
    assertThat(root.path("data").path("status").asText()).isEqualTo("succeeded");
  }

  @Test
  void javaCanReachDifySetupEndpoint() throws Exception {
    String baseUrl = System.getenv().getOrDefault("DIFY_BASE_URL", "http://[::1]:8501/v1");
    String setupUrl = baseUrl.replaceAll("/v1/?$", "") + "/console/api/setup";
    HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(setupUrl))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
    HttpResponse<String> response = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("finished");
  }
}
