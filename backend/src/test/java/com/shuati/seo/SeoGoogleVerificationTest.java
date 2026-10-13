package com.shuati.seo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Google Search Console 的 HTML 标记验证：
 * 首页是后端直出的，所以 meta 必须出现在这里，只写在 SPA 外壳里 Google 验不过。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "shuati.seo.google-site-verification=google1234567890abcdef")
class SeoGoogleVerificationTest {

  @Autowired SeoController controller;

  @Test
  void emitsGoogleVerificationMetaWhenConfigured() {
    String home = controller.home().getBody();
    assertThat(home).contains(
        "<meta name=\"google-site-verification\" content=\"google1234567890abcdef\">");

    // 深一层的页面也带上，方便按任意 URL 前缀建资源
    assertThat(controller.sitemap().getBody()).isNotBlank();
  }
}
