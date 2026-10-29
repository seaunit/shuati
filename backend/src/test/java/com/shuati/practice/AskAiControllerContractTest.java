package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuati.rate.RateLimit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;

class AskAiControllerContractTest {

  @Test
  void askEndpointRequiresPostAndUserRateLimit() throws Exception {
    var method = PracticeController.class.getMethod(
        "askAi", long.class, Map.class);

    PostMapping post = method.getAnnotation(PostMapping.class);
    RateLimit rateLimit = method.getAnnotation(RateLimit.class);

    assertThat(post).isNotNull();
    assertThat(post.value()).containsExactly("/api/practice/questions/{id}/ask-ai");
    assertThat(rateLimit).isNotNull();
    assertThat(rateLimit.name()).isEqualTo("ai:ask");
    assertThat(rateLimit.limit()).isEqualTo(20);
    assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    assertThat(rateLimit.scope()).isEqualTo(RateLimit.Scope.USER);
  }

  @Test
  void askServiceNeverWrapsAiCallInDatabaseTransaction() {
    assertThat(AskAiService.class.getAnnotation(Transactional.class)).isNull();
  }
}
