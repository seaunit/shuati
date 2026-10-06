package com.shuati.rate;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shuati.rate-limit")
public class RateLimitProperties {

  /** 总开关 */
  private boolean enabled = true;

  /** 全局兜底：同一 IP 每分钟最多请求次数（0 表示关闭） */
  private int globalPerMinute = 300;
}
