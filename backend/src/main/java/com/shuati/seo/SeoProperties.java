package com.shuati.seo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SEO 站点的对外信息。
 *
 * <p>site-url 是写进 canonical / sitemap / robots 的规范域名，必须和百度搜索资源平台里
 * 验证过的那个站点一致（当前是 www 版本），否则收录会分到两个域名上。
 */
@ConfigurationProperties(prefix = "shuati.seo")
public record SeoProperties(
    String siteUrl,
    String siteName,
    String tagline,
    String description,
    String keywords,
    String contactEmail,
    String baiduTongjiId) {

  /** 去掉结尾斜杠，拼路径时不会出现双斜杠 */
  public String base() {
    String url = siteUrl == null ? "" : siteUrl.trim();
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    return url;
  }

  public String absolute(String path) {
    return base() + path;
  }
}
