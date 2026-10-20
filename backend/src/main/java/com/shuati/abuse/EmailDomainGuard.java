package com.shuati.abuse;

import com.shuati.common.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 一次性邮箱（临时邮箱）域名拦截。
 *
 * <p>为什么用精确集合而不是布隆过滤器：名单是几百到几千条，内存不到 1MB，判断是 O(标签数)；
 * 布隆有假阳性，而注册是不可逆动作——误伤一个真实用户比漏掉一个脚本更糟。
 * 将来名单上到十万级、或多实例要共享大集合时，才用 Redis 位图做**前置筛**，命中后回查本集合确认。
 *
 * <p>匹配规则：邮箱取最后一个 {@code @} 之后的域名 → 归一化（trim/小写/去尾点/IDN→punycode）
 * → 按标签从右往左逐级比对。这样 {@code a.b.mailinator.com} 命中 {@code mailinator.com}，
 * 而 {@code notmailinator.com} 不会。
 */
@Component
public class EmailDomainGuard {

  private static final Logger log = LoggerFactory.getLogger(EmailDomainGuard.class);

  private final Set<String> blockedDomains;

  public EmailDomainGuard(
      @Value("classpath:abuse/disposable-domains.txt") Resource resource,
      AbuseGuardProperties properties) throws IOException {
    Set<String> loaded = new LinkedHashSet<>();
    int lineNo = 0;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        String domain = normalizeDomain(line);
        if (domain.isEmpty()) {
          continue;
        }
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
          // 配置错误要在启动时炸出来，而不是静默失效
          throw new IllegalStateException(
              "abuse/disposable-domains.txt 第 " + lineNo + " 行不是合法域名：" + line);
        }
        loaded.add(domain);
      }
    }
    int builtin = loaded.size();
    for (String extra : properties.getBlockedEmailDomains()) {
      String domain = normalizeDomain(extra);
      if (!domain.isEmpty()) {
        loaded.add(domain);
      }
    }
    this.blockedDomains = Collections.unmodifiableSet(loaded);
    log.info("一次性邮箱名单已加载：{} 条（内置 {}，环境变量追加 {}）",
        blockedDomains.size(), builtin, blockedDomains.size() - builtin);
  }

  /** 只做判断，不抛异常（给错误提示等场景用）。 */
  public boolean isBlocked(String email) {
    String domain = domainOf(email);
    if (domain.isEmpty()) {
      return false;
    }
    // 从右往左按标签比对，避免 a.b.mailinator.com 这类子域绕过
    String candidate = domain;
    while (true) {
      if (blockedDomains.contains(candidate)) {
        return true;
      }
      int dot = candidate.indexOf('.');
      if (dot < 0) {
        return false;
      }
      candidate = candidate.substring(dot + 1);
    }
  }

  /** 命中即拒绝并记录脱敏日志；未命中直接返回。 */
  public void assertAllowed(String email, String ip, String userAgent) {
    if (!isBlocked(email)) {
      return;
    }
    log.warn("register.blocked domain={} email={} ip={} ua={}",
        domainOf(email), mask(email), safe(ip), safe(userAgent));
    throw new ApiException(400, "请使用常用邮箱，临时邮箱暂不支持");
  }

  static String domainOf(String email) {
    if (email == null) {
      return "";
    }
    String value = email.trim();
    int at = value.lastIndexOf('@');
    if (at < 0 || at == value.length() - 1) {
      return "";
    }
    return normalizeDomain(value.substring(at + 1));
  }

  /** trim → 去注释 → 去尾点 → 小写 → IDN 转 punycode；非法输入返回空串。 */
  static String normalizeDomain(String raw) {
    if (raw == null) {
      return "";
    }
    String value = raw.trim();
    if (value.isEmpty() || value.startsWith("#")) {
      return "";
    }
    while (value.endsWith(".")) {
      value = value.substring(0, value.length() - 1);
    }
    value = value.toLowerCase(Locale.ROOT);
    try {
      return IDN.toASCII(value);
    } catch (IllegalArgumentException e) {
      return "";
    }
  }

  private String mask(String email) {
    if (email == null) {
      return "";
    }
    String value = email.trim();
    int at = value.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    return value.charAt(0) + "***@" + value.substring(at + 1);
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "-" : value.trim();
  }
}
