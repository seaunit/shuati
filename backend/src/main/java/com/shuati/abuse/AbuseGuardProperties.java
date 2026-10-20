package com.shuati.abuse;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 注册风控的可调参数，全部可用环境变量覆盖（见 application.yml 的 shuati.abuse）。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shuati.abuse")
public class AbuseGuardProperties {

  /** 追加的一次性邮箱域名（逗号分隔），与 classpath:abuse/disposable-domains.txt 合并 */
  private List<String> blockedEmailDomains = new ArrayList<>();

  /** 同一 IP 每小时最多注册次数（0 = 关闭） */
  private int registerIpHourlyLimit = 3;

  /** 同一 IP 每天最多注册次数（0 = 关闭） */
  private int registerIpDailyLimit = 10;

  /** 新账号 24 小时内 AI 调用上限（0 = 关闭） */
  private int newAccountAiDailyLimit = 30;
}
