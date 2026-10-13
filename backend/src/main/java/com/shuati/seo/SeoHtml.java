package com.shuati.seo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 给爬虫看的静态 HTML 页面外壳。
 *
 * <p>百度蜘蛛对 JS 渲染很弱，所以公共题库这类内容直接由后端吐完整 HTML：不依赖任何
 * 前端资源，样式内联在 head 里，一次请求就能拿到全部正文。
 */
@Component
@RequiredArgsConstructor
public class SeoHtml {

  private final SeoProperties seo;

  /**
   * 页面参数：标题、描述、canonical 路径、正文，以及可选的 head 追加片段
   * （结构化数据 JSON-LD 之类的，只有部分页面需要）。
   */
  public record Page(
      String title, String description, String path, String body, String extraHead) {

    public Page(String title, String description, String path, String body) {
      this(title, description, path, body, "");
    }
  }

  public String render(Page page) {
    String title = escape(page.title());
    String description = escape(page.description());
    String canonical = seo.absolute(page.path());
    String siteName = escape(seo.siteName());

    StringBuilder html = new StringBuilder(8192);
    html.append("<!doctype html>\n<html lang=\"zh-CN\">\n<head>\n");
    html.append("<meta charset=\"utf-8\">\n");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    html.append("<meta name=\"applicable-device\" content=\"pc,mobile\">\n");
    html.append("<title>").append(title).append("</title>\n");
    html.append("<meta name=\"description\" content=\"").append(description).append("\">\n");
    html.append("<meta name=\"keywords\" content=\"").append(escape(seo.keywords()))
        .append("\">\n");
    html.append("<link rel=\"canonical\" href=\"").append(canonical).append("\">\n");
    html.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/favicon.svg\">\n");
    html.append("<meta property=\"og:type\" content=\"website\">\n");
    html.append("<meta property=\"og:site_name\" content=\"").append(siteName).append("\">\n");
    html.append("<meta property=\"og:title\" content=\"").append(title).append("\">\n");
    html.append("<meta property=\"og:description\" content=\"").append(description)
        .append("\">\n");
    html.append("<meta property=\"og:url\" content=\"").append(canonical).append("\">\n");
    html.append("<style>").append(CSS).append("</style>\n");
    if (page.extraHead() != null && !page.extraHead().isBlank()) {
      html.append(page.extraHead()).append('\n');
    }
    html.append(tongji()).append('\n');
    html.append("</head>\n<body>\n");

    html.append("<header class=\"top\">\n");
    html.append("<a class=\"brand\" href=\"/\">").append(siteName).append("</a>\n");
    html.append("""
          <nav class="nav">
            <a href="/">首页</a>
            <a href="/app">题库</a>
            <a href="/login">登录 / 注册</a>
          </nav>
        </header>
        <main>
        """);
    html.append(page.body());
    html.append("\n</main>\n<footer class=\"foot\">\n<p>\n");
    html.append("<a href=\"/terms\">服务条款</a>\n");
    html.append("<a href=\"/privacy\">隐私政策</a>\n");
    html.append("<a href=\"mailto:").append(escape(seo.contactEmail()))
        .append("\">客服邮箱</a>\n");
    html.append("<span>© 2026 ").append(siteName).append("</span>\n");
    html.append("</p>\n</footer>\n</body>\n</html>\n");
    return html.toString();
  }

  /** 百度统计：SEO 落地页也要统计，不然看不出搜索带来的访问 */
  private String tongji() {
    String id = seo.baiduTongjiId();
    if (id == null || id.isBlank()) {
      return "";
    }
    return """
        <script>
        var _hmt = _hmt || [];
        (function() {
          var hm = document.createElement("script");
          hm.src = "https://hm.baidu.com/hm.js?%s";
          var s = document.getElementsByTagName("script")[0];
          s.parentNode.insertBefore(hm, s);
        })();
        </script>""".formatted(escape(id.trim()));
  }

  /** 正文里的换行用 pre-wrap 呈现，Markdown 代码块也能保持原样，不做额外的解析 */
  public static String text(String value) {
    return "<div class=\"text\">" + escape(value) + "</div>";
  }

  public static String escape(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static final String CSS = """
      :root{color-scheme:light}
      *{box-sizing:border-box}
      body{margin:0;background:#fff;color:#17181a;
        font:16px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif}
      a{color:#2f6b45;text-decoration:none}
      a:hover{text-decoration:underline}
      .top{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;
        padding:14px 20px;border-bottom:1px solid #e9ebee}
      .brand{font-weight:600;font-size:18px;color:#17181a}
      .nav{display:flex;flex-wrap:wrap;gap:14px;font-size:14px}
      main{max-width:880px;margin:0 auto;padding:24px 20px 8px}
      h1{font-size:24px;line-height:1.4;margin:0 0 8px}
      h2{font-size:18px;margin:28px 0 10px}
      h3{font-size:16px;margin:20px 0 6px}
      h4{font-size:15px;margin:14px 0 4px}
      p{margin:8px 0;color:#17181a}
      .lead{color:#6b7280}
      .crumb{font-size:13px;color:#6b7280;margin-bottom:12px}
      .crumb a{margin-right:4px}
      .cta{display:inline-block;margin:14px 0 4px;padding:10px 18px;border-radius:12px;
        background:#17181a;color:#fff;font-size:15px}
      .cta:hover{text-decoration:none;opacity:.9}
      .cta.ghost{background:#fff;color:#17181a;border:1px solid #e9ebee}
      .sibling{display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between;font-size:14px;
        border-top:1px solid #e9ebee;border-bottom:1px solid #e9ebee;padding:12px 0;margin:22px 0}
      .card{border:1px solid #e9ebee;border-radius:14px;padding:14px 16px;margin:10px 0}
      .list{list-style:none;margin:0;padding:0}
      .list li{border-bottom:1px solid #e9ebee;padding:10px 2px;display:flex;gap:10px;
        justify-content:space-between;align-items:baseline}
      .list li:last-child{border-bottom:none}
      .meta{color:#6b7280;font-size:13px;white-space:nowrap}
      .text{white-space:pre-wrap;word-break:break-word}
      .opt{margin:2px 0 2px 2px;color:#17181a}
      .answer{background:#e9ebee;border-radius:8px;padding:10px 12px;margin:8px 0;font-size:15px}
      .tag{display:inline-block;background:#e9ebee;border-radius:6px;padding:1px 8px;
        font-size:12px;color:#6b7280;margin-right:6px}
      .foot{border-top:1px solid #e9ebee;margin-top:32px;padding:16px 20px 28px;font-size:13px;color:#6b7280}
      .foot p{margin:0;display:flex;flex-wrap:wrap;gap:14px}
      @media (max-width:520px){h1{font-size:20px}main{padding:18px 16px 4px}}
      """;
}
