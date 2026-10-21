package com.shuati.importer;

import com.shuati.common.ApiException;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 网页抓取的合规闸门。
 *
 * <p>规则（用户要求）：<b>只抓 robots.txt 允许的页面</b>。抓取前先取该站点的 /robots.txt，
 * 按我们自己的 UA（ShuatiBot）解析；明确禁止的页面一律不抓，返回 403 并引导用户改用「截图识别」。
 *
 * <p>同时做 SSRF 防护：只允许 http/https，解析出的地址不能是本机、内网、链路本地或云元数据地址，
 * 每一跳重定向都重新校验（防止用 302 绕到内网）。
 */
@Component
public class RobotsPolicy {

  static final String BOT_NAME = "ShuatiBot";
  static final String USER_AGENT = "ShuatiBot/1.0 (+https://www.seaunit.site)";

  private static final int MAX_ROBOTS_CHARS = 512 * 1024;
  private static final int MAX_PAGE_CHARS = 2 * 1024 * 1024;
  private static final int MAX_REDIRECTS = 3;

  static final String DISALLOWED_MESSAGE =
      "该网页不允许爬取（robots.txt 禁止），请改用「截图识别」：在浏览器打开页面后截图粘贴";

  /** 纯逻辑，便于单测：给定 robots.txt 内容，判断 target 是否允许抓取。 */
  static boolean isAllowedByRobots(String robotsTxt, String robotsUrl, String targetUrl) {
    if (robotsTxt == null || robotsTxt.isBlank()) {
      return true; // 没有 robots.txt = 允许
    }
    BaseRobotRules rules = new SimpleRobotRulesParser()
        .parseContent(robotsUrl, robotsTxt.getBytes(StandardCharsets.UTF_8), "text/plain", BOT_NAME);
    return rules.isAllowed(targetUrl);
  }

  /** 本机 / 内网 / 链路本地 / 云元数据地址一律拒绝（SSRF）。 */
  static boolean isPrivateAddress(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] raw = address.getAddress();
    if (address instanceof Inet4Address && raw.length == 4) {
      int first = raw[0] & 0xff;
      int second = raw[1] & 0xff;
      // 100.64.0.0/10 运营商级 NAT；0.0.0.0/8 保留
      return (first == 100 && second >= 64 && second <= 127) || first == 0;
    }
    if (address instanceof Inet6Address && raw.length == 16) {
      int first = raw[0] & 0xff;
      return (first & 0xfe) == 0xfc; // fc00::/7 唯一本地地址
    }
    return false;
  }

  /** 抓取前调用：协议 + SSRF + robots.txt。不允许时抛 403。 */
  public void assertCrawlAllowed(String rawUrl) {
    URI target = parseHttpUri(rawUrl);
    validateTarget(target);
    String robotsUrl = target.resolve("/robots.txt").toString();
    String robots = fetchRobots(target.resolve("/robots.txt"));
    if (!isAllowedByRobots(robots, robotsUrl, target.toString())) {
      throw new ApiException(403, DISALLOWED_MESSAGE);
    }
  }

  /** 抓取网页正文（先过 robots 校验）。 */
  public String fetchPage(String rawUrl) {
    assertCrawlAllowed(rawUrl);
    URI target = parseHttpUri(rawUrl);
    FetchResult result = get(target, MAX_PAGE_CHARS);
    if (result.status() >= 400) {
      throw new ApiException(400, "抓取网页失败（HTTP " + result.status() + "）");
    }
    return result.body() == null ? "" : result.body();
  }

  private String fetchRobots(URI robotsUri) {
    try {
      FetchResult result = get(robotsUri, MAX_ROBOTS_CHARS);
      if (result.status() == 404 || result.status() == 410) {
        return ""; // 没有 robots.txt = 允许
      }
      if (result.status() >= 400) {
        throw new ApiException(403,
            "无法确认该网页是否允许爬取（robots.txt HTTP " + result.status()
                + "），请改用「截图识别」");
      }
      return result.body() == null ? "" : result.body();
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(403, "无法确认该网页是否允许爬取，请改用「截图识别」");
    }
  }

  private FetchResult get(URI start, int maxChars) {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    URI current = start;
    try {
      for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
        validateTarget(current);
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(current)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
          String location = response.headers().firstValue("Location").orElse(null);
          if (location == null || location.isBlank()) {
            throw new ApiException(400, "网页重定向缺少目标地址");
          }
          current = current.resolve(location.trim());
          continue;
        }
        String body = response.body();
        if (body != null && body.length() > maxChars) {
          throw new ApiException(400, "页面内容过大，请改用「截图识别」");
        }
        return new FetchResult(status, body);
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(400, "抓取网页失败：" + e.getMessage());
    }
    throw new ApiException(400, "网页重定向次数过多");
  }

  private URI parseHttpUri(String rawUrl) {
    try {
      URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
      String scheme = uri.getScheme();
      if (scheme == null
          || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getHost() == null || uri.getHost().isBlank()) {
        throw new ApiException(400, "请输入完整的 http/https 网页链接");
      }
      return uri;
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "网页链接格式不正确");
    }
  }

  private void validateTarget(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null
        || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      throw new ApiException(400, "只支持 http/https 网页链接");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new ApiException(400, "网页链接缺少主机名");
    }
    try {
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (isPrivateAddress(address)) {
          throw new ApiException(403, "不允许抓取内网或本机地址，请改用「截图识别」");
        }
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(400, "网页域名解析失败：" + host);
    }
  }

  private record FetchResult(int status, String body) {
  }
}
