package com.shuati.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DifyWorkflowConfiguration {

  @Bean
  @ConditionalOnProperty(name = "shuati.ai.engine", havingValue = "dify")
  DifyWorkflowClient difyWorkflowClient(
      DifyWorkflowProperties properties,
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper) {
    properties.validateTimeouts();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getConnectTimeout());
    requestFactory.setReadTimeout(properties.getReadTimeout());
    RestClient restClient = restClientBuilder.requestFactory(requestFactory).build();
    return new DifyWorkflowClient(properties, objectMapper, restClient);
  }
}
