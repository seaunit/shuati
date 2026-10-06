package com.shuati.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shuati.captcha")
public class CaptchaProperties {

  /** 有效期（秒），Redis TTL 自动过期 */
  private int ttlSeconds = 120;

  /** 单个 ticket 最多校验次数，超过直接作废 */
  private int maxAttempts = 5;

  private int codeLength = 4;

  private int width = 150;

  private int height = 48;

  /** true=加盐哈希存储（推荐）；false=明文存储 */
  private boolean hashStore = true;

  private String secret = "change-me";

  private int ipLimitPerMinute = 20;

  private int deviceLimitPerMinute = 20;
}
