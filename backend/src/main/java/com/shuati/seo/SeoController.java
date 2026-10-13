package com.shuati.seo;

import com.shuati.common.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向搜索引擎的公开页面（百度蜘蛛不执行 JS，所以这些页面直接吐完整 HTML）。
 *
 * <p>层级是「首页 → 题库 → 单元 → 题目」四层，每层都只列下一层的入口，形成一条
 * 蜘蛛能顺着爬、用户也能顺着点的路径：
 *
 * <ul>
 *   <li>{@code /} 落地页：产品说明 + 公共题库
 *   <li>{@code /bank/{id}} 题库页：单元列表
 *   <li>{@code /bank/{id}/unit/{uid}} 单元页：本单元全部题目（题干、选项、答案、解析）
 *   <li>{@code /bank/{id}/question/{qid}} 题目页：单题聚焦页，标题就是题干，吃长尾搜索
 * </ul>
 *
 * <p>只暴露公共题库（owner_id 为空）：用户自己建的题库是私有的，不参与收录。
 * 每页都带 canonical、面包屑和结构化数据。
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

  private static final String HTML = "text/html;charset=UTF-8";
  private static final String XML = "application/xml;charset=UTF-8";
  /**
   * 直出页面很少变（题库、单元、题目+解析都是低频写入），给边缘一段缓存时间：
   * 国内到 Cloudflare 边缘本身就有几百毫秒，回源那一段能省掉就省掉。
   */
  private static final String CACHE_HTML =
      "public, max-age=300, s-maxage=600, stale-while-revalidate=86400";
  private static final String CACHE_XML = "public, max-age=1800";
  private static final int MAX_QUESTIONS_PER_PAGE = 300;
  private static final int SITEMAP_LIMIT = 5000;

  private final JdbcTemplate jdbc;
  private final SeoHtml html;
  private final SeoProperties seo;
  private final Json json;

  /** 首页落地页：讲清产品是什么，并把公共题库列出来给蜘蛛爬 */
  @GetMapping(value = "/", produces = HTML)
  public ResponseEntity<String> home() {
    List<Map<String, Object>> banks = jdbc.queryForList("""
        select b.id, b.name, b.description,
               (select count(*) from unit u where u.bank_id = b.id) as unit_count,
               (select count(*) from question q join unit u2 on u2.id = q.unit_id
                 where u2.bank_id = b.id and q.status = 'ON') as question_count
          from bank b
         where b.owner_id is null
         order by b.id asc
        """);

    StringBuilder body = new StringBuilder();
    body.append("<h1>").append(SeoHtml.escape(seo.siteName())).append(" · ")
        .append(SeoHtml.escape(seo.tagline())).append("</h1>");
    body.append("<p class=\"lead\">").append(SeoHtml.escape(seo.description())).append("</p>");
    body.append("""
        <h2>能做什么</h2>
        <ul>
          <li>公共题库免费刷，选择题本地判分、简答题 AI 判分</li>
          <li>上传自己的讲义或真题文档，自动切成题库</li>
          <li>每道题都能让 AI 讲一遍，解析会存下来下次直接看</li>
          <li>错题本与掌握度统计，按单元记录练习进度</li>
        </ul>
        """);

    body.append("<h2>公共题库</h2>");
    if (banks.isEmpty()) {
      body.append("<p class=\"lead\">题库正在整理中。</p>");
    } else {
      body.append("<ul class=\"list\">");
      for (Map<String, Object> bank : banks) {
        long bankId = number(bank.get("id"));
        body.append("<li><a href=\"/bank/").append(bankId).append("\">")
            .append(SeoHtml.escape(string(bank.get("name")))).append("</a>")
            .append("<span class=\"meta\">").append(number(bank.get("unit_count")))
            .append(" 个单元 · ").append(number(bank.get("question_count")))
            .append(" 道题</span></li>");
      }
      body.append("</ul>");
    }
    body.append("<a class=\"cta\" href=\"/app\">进入题库开始刷题</a>");

    String ld = jsonLd(Map.of(
        "@context", "https://schema.org",
        "@graph", List.of(
            Map.of("@type", "WebSite",
                "name", seo.siteName(),
                "url", seo.base(),
                "inLanguage", "zh-CN"),
            Map.of("@type", "Organization",
                "name", seo.siteName(),
                "url", seo.base(),
                "email", seo.contactEmail()))));

    return page(new SeoHtml.Page(
        seo.siteName() + " - " + seo.tagline(), seo.description(), "/", body.toString(), ld));
  }

  /** 题库页：列出单元，链到每个单元的题目页 */
  @GetMapping(value = "/bank/{bankId}", produces = HTML)
  public ResponseEntity<String> bank(@PathVariable long bankId) {
    List<Map<String, Object>> banks = jdbc.queryForList("""
        select id, name, description from bank where id = ? and owner_id is null
        """, bankId);
    if (banks.isEmpty()) {
      return notFound();
    }
    Map<String, Object> bank = banks.get(0);
    String bankName = string(bank.get("name"));

    List<Map<String, Object>> units = jdbc.queryForList("""
        select u.id, u.name,
               (select count(*) from question q where q.unit_id = u.id and q.status = 'ON')
                 as question_count
          from unit u
         where u.bank_id = ?
         order by u.sort asc, u.id asc
        """, bankId);

    long total = units.stream().mapToLong(unit -> number(unit.get("question_count"))).sum();

    StringBuilder body = new StringBuilder();
    body.append(crumb("/", "首页", bankName)).append("<h1>")
        .append(SeoHtml.escape(bankName)).append(" · 题目与答案</h1>");
    String description = string(bank.get("description"));
    if (!description.isBlank()) {
      body.append("<p class=\"lead\">").append(SeoHtml.escape(description)).append("</p>");
    }
    body.append("<p class=\"meta\">共 ").append(units.size()).append(" 个单元，")
        .append(total).append(" 道题</p>");

    body.append("<h2>单元</h2>");
    if (units.isEmpty()) {
      body.append("<p class=\"lead\">这个题库还没有单元。</p>");
    } else {
      body.append("<ul class=\"list\">");
      for (Map<String, Object> unit : units) {
        body.append("<li><a href=\"/bank/").append(bankId).append("/unit/")
            .append(number(unit.get("id"))).append("\">")
            .append(SeoHtml.escape(string(unit.get("name")))).append("</a>")
            .append("<span class=\"meta\">").append(number(unit.get("question_count")))
            .append(" 题</span></li>");
      }
      body.append("</ul>");
    }
    body.append("<a class=\"cta\" href=\"/app/practice?bankId=").append(bankId)
        .append("\">开始刷这套题</a>");

    List<Object> items = new ArrayList<>();
    items.add(Map.of("@type", "ListItem", "position", 1, "name", bankName,
        "item", seo.absolute("/bank/" + bankId)));
    String ld = breadcrumbLd(List.of(
        Map.of("name", "首页", "path", "/"),
        Map.of("name", bankName, "path", "/bank/" + bankId)))
        + jsonLd(Map.of("@context", "https://schema.org", "@type", "ItemList",
            "itemListElement", items));

    return page(new SeoHtml.Page(
        bankName + " - 题目与答案 - " + seo.siteName(),
        bankName + "共 " + total + " 道题，覆盖 " + units.size() + " 个单元，题目含答案与解析。",
        "/bank/" + bankId, body.toString(), ld));
  }

  /** 单元页：本单元全部题目（题干、选项、答案、解析），并链到单题详情页 */
  @GetMapping(value = "/bank/{bankId}/unit/{unitId}", produces = HTML)
  public ResponseEntity<String> unit(@PathVariable long bankId, @PathVariable long unitId) {
    List<Map<String, Object>> units = jdbc.queryForList("""
        select u.id, u.name, u.sort, b.name as bank_name
          from unit u join bank b on b.id = u.bank_id
         where u.id = ? and u.bank_id = ? and b.owner_id is null
        """, unitId, bankId);
    if (units.isEmpty()) {
      return notFound();
    }
    Map<String, Object> unit = units.get(0);
    String unitName = string(unit.get("name"));
    String bankName = string(unit.get("bank_name"));

    List<Map<String, Object>> questions = questionsOfUnit(unitId);

    StringBuilder body = new StringBuilder();
    body.append(crumb("/bank/" + bankId, bankName, unitName))
        .append("<h1>").append(SeoHtml.escape(unitName)).append(" - ")
        .append(SeoHtml.escape(bankName)).append("题目与答案</h1>");
    body.append("<p class=\"lead\">本单元共 ").append(questions.size())
        .append(" 道题，每题给答案，部分带解析。</p>");

    if (questions.isEmpty()) {
      body.append("<p class=\"lead\">这个单元还没有上线的题目。</p>");
    }
    int index = 1;
    for (Map<String, Object> question : questions) {
      body.append(questionCard(bankId, question, index++));
    }
    body.append(siblingUnits(bankId, number(unit.get("sort")), unitId));
    body.append("<a class=\"cta\" href=\"/app/practice?unitId=").append(unitId)
        .append("\">开始刷这个单元</a>");

    String ld = breadcrumbLd(List.of(
        Map.of("name", "首页", "path", "/"),
        Map.of("name", bankName, "path", "/bank/" + bankId),
        Map.of("name", unitName, "path", "/bank/" + bankId + "/unit/" + unitId)));

    return page(new SeoHtml.Page(
        unitName + " - " + bankName + "题目与答案 - " + seo.siteName(),
        bankName + unitName + "共 " + questions.size()
            + " 道题，含答案与解析，可在拾题在线刷题并记录错题。",
        "/bank/" + bankId + "/unit/" + unitId, body.toString(), ld));
  }

  /** 题目详情页：一道题一个页面，标题就是题干，承接长尾搜索 */
  @GetMapping(value = "/bank/{bankId}/question/{questionId}", produces = HTML)
  public ResponseEntity<String> question(
      @PathVariable long bankId, @PathVariable long questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.id, q.unit_id, q.type, q.content, q.options, q.answer, q.explanation, q.difficulty,
               u.name as unit_name, b.name as bank_name
          from question q
          join unit u on u.id = q.unit_id
          join bank b on b.id = u.bank_id
         where q.id = ? and u.bank_id = ? and b.owner_id is null and q.status = 'ON'
        """, questionId, bankId);
    if (rows.isEmpty()) {
      return notFound();
    }
    Map<String, Object> question = rows.get(0);
    long unitId = number(question.get("unit_id"));
    String unitName = string(question.get("unit_name"));
    String bankName = string(question.get("bank_name"));

    String answer = string(question.get("answer"));
    String content = string(question.get("content"));
    String typeLabel = typeLabel(string(question.get("type")));

    List<Map<String, Object>> siblings = questionsOfUnit(unitId);
    int index = -1;
    for (int i = 0; i < siblings.size(); i++) {
      if (number(siblings.get(i).get("id")) == questionId) {
        index = i;
        break;
      }
    }

    StringBuilder body = new StringBuilder();
    body.append("<div class=\"crumb\"><a href=\"/\">首页</a> / <a href=\"/bank/")
        .append(bankId).append("\">").append(SeoHtml.escape(bankName)).append("</a> / <a href=\"")
        .append("/bank/").append(bankId).append("/unit/").append(unitId).append("\">")
        .append(SeoHtml.escape(unitName)).append("</a> / 第 ").append(index + 1)
        .append(" 题</div>");
    body.append("<h1>").append(SeoHtml.escape(summary(content, 60))).append("</h1>");
    body.append(questionCard(bankId, question, 0));

    if (index > 0) {
      Map<String, Object> prev = siblings.get(index - 1);
      body.append("<a class=\"cta ghost\" href=\"/bank/").append(bankId).append("/question/")
          .append(number(prev.get("id"))).append("\">← 上一题</a> ");
    }
    if (index >= 0 && index < siblings.size() - 1) {
      Map<String, Object> next = siblings.get(index + 1);
      body.append("<a class=\"cta ghost\" href=\"/bank/").append(bankId).append("/question/")
          .append(number(next.get("id"))).append("\">下一题 →</a>");
    }

    // 同单元的其他题目：给蜘蛛一条继续爬、给用户一条继续刷的路
    body.append("<h2>本单元其他题目</h2>");
    body.append("<ul class=\"list\">");
    for (Map<String, Object> sibling : siblings) {
      long siblingId = number(sibling.get("id"));
      if (siblingId == questionId) {
        continue;
      }
      body.append("<li><a href=\"/bank/").append(bankId).append("/question/").append(siblingId)
          .append("\">").append(SeoHtml.escape(summary(string(sibling.get("content")), 48)))
          .append("</a><span class=\"meta\">")
          .append(typeLabel(string(sibling.get("type")))).append("</span></li>");
    }
    body.append("</ul>");
    body.append("<a class=\"cta\" href=\"/app/practice?unitId=").append(unitId)
        .append("\">在拾题里做这套题</a>");

    String ld = breadcrumbLd(List.of(
        Map.of("name", "首页", "path", "/"),
        Map.of("name", bankName, "path", "/bank/" + bankId),
        Map.of("name", unitName, "path", "/bank/" + bankId + "/unit/" + unitId),
        Map.of("name", summary(content, 24), "path",
            "/bank/" + bankId + "/question/" + questionId)))
        + jsonLd(Map.of(
            "@context", "https://schema.org",
            "@type", "Question",
            "name", content,
            "text", content,
            "acceptedAnswer", Map.of("@type", "Answer", "text", answer),
            "isPartOf", Map.of("@type", "Quiz", "name", unitName)));

    return page(new SeoHtml.Page(
        summary(content, 30) + " 答案与解析 - " + seo.siteName(),
        typeLabel + "：" + summary(content, 60) + "。正确答案：" + summary(answer, 40),
        "/bank/" + bankId + "/question/" + questionId, body.toString(), ld));
  }

  /** sitemap：首页 + 合规页 + 所有公共题库、单元与题目 */
  @GetMapping(value = "/sitemap.xml", produces = XML)
  public ResponseEntity<String> sitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    xml.append(entry("/", "1.0", "daily", ""));
    xml.append(entry("/terms", "0.3", "monthly", ""));
    xml.append(entry("/privacy", "0.3", "monthly", ""));

    // lastmod 是 Google 真正会用的字段，用它告诉搜索引挈这一页什么时候变的
    LinkedHashMap<String, String> pages = new LinkedHashMap<>();
    for (Map<String, Object> row : jdbc.queryForList(
        "select id, created_at from bank where owner_id is null order by id asc")) {
      pages.put("/bank/" + number(row.get("id")), lastmod(row.get("created_at")));
    }
    for (Map<String, Object> row : jdbc.queryForList("""
        select b.id as bank_id, u.id as unit_id,
               coalesce(max(q.updated_at), b.created_at) as last_at
          from unit u
          join bank b on b.id = u.bank_id
          left join question q on q.unit_id = u.id
         where b.owner_id is null
         group by b.id, u.id, b.created_at
         order by b.id asc, u.id asc
        """)) {
      pages.put("/bank/" + number(row.get("bank_id")) + "/unit/" + number(row.get("unit_id")),
          lastmod(row.get("last_at")));
    }
    for (Map<String, Object> row : jdbc.queryForList("""
        select b.id as bank_id, q.id as question_id, q.updated_at
          from question q join unit u on u.id = q.unit_id join bank b on b.id = u.bank_id
         where b.owner_id is null and q.status = 'ON'
         order by b.id asc, u.sort asc, q.id asc
         limit %d
        """.formatted(SITEMAP_LIMIT))) {
      pages.put("/bank/" + number(row.get("bank_id")) + "/question/"
          + number(row.get("question_id")), lastmod(row.get("updated_at")));
    }
    for (Map.Entry<String, String> page : pages.entrySet()) {
      xml.append(entry(page.getKey(),
          page.getKey().contains("/question/") ? "0.6" : "0.8", "weekly", page.getValue()));
    }
    xml.append("</urlset>\n");
    return ResponseEntity.ok()
        .header("Content-Type", XML)
        .header("Cache-Control", CACHE_XML)
        .body(xml.toString());
  }

  /** 一道题的卡片；index 为 0 时不显示题号（详情页用） */
  private String questionCard(long bankId, Map<String, Object> question, int index) {
    String type = string(question.get("type"));
    StringBuilder card = new StringBuilder();
    card.append("<div class=\"card\">");
    card.append("<h3>");
    if (index > 0) {
      card.append("<a href=\"/bank/").append(bankId).append("/question/")
          .append(number(question.get("id"))).append("\">").append(index).append(". ")
          .append(typeLabel(type)).append("</a>");
    } else {
      card.append(typeLabel(type));
    }
    card.append("</h3>");
    card.append(SeoHtml.text(string(question.get("content"))));

    List<?> options = asList(question.get("options"));
    if (!options.isEmpty()) {
      card.append("<div>");
      for (Object option : options) {
        if (option instanceof Map<?, ?> map) {
          card.append("<p class=\"opt\">").append(SeoHtml.escape(string(map.get("key"))))
              .append(". ").append(SeoHtml.escape(string(map.get("text")))).append("</p>");
        }
      }
      card.append("</div>");
    }

    card.append("<div class=\"answer\">答案：")
        .append(SeoHtml.escape(string(question.get("answer")))).append("</div>");
    String explanation = string(question.get("explanation"));
    if (!explanation.isBlank()) {
      card.append("<h4>解析</h4>").append(SeoHtml.markdown(explanation));
    }
    card.append("</div>");
    return card.toString();
  }

  /** 上一单元 / 下一单元 / 返回题库：单元之间的内链，方便蜘蛛遍历整站 */
  private String siblingUnits(long bankId, long sort, long unitId) {
    Map<String, Object> prev = one("""
        select id, name from unit
         where bank_id = ? and (sort < ? or (sort = ? and id < ?))
         order by sort desc, id desc limit 1
        """, bankId, sort, sort, unitId);
    Map<String, Object> next = one("""
        select id, name from unit
         where bank_id = ? and (sort > ? or (sort = ? and id > ?))
         order by sort asc, id asc limit 1
        """, bankId, sort, sort, unitId);
    StringBuilder nav = new StringBuilder("<nav class=\"sibling\">");
    if (prev != null) {
      nav.append("<a href=\"/bank/").append(bankId).append("/unit/")
          .append(number(prev.get("id"))).append("\">← ")
          .append(SeoHtml.escape(summary(string(prev.get("name")), 20))).append("</a>");
    }
    nav.append("<a href=\"/bank/").append(bankId).append("\">全部单元</a>");
    if (next != null) {
      nav.append("<a href=\"/bank/").append(bankId).append("/unit/")
          .append(number(next.get("id"))).append("\">")
          .append(SeoHtml.escape(summary(string(next.get("name")), 20))).append(" →</a>");
    }
    nav.append("</nav>");
    return nav.toString();
  }

  private List<Map<String, Object>> questionsOfUnit(long unitId) {
    return jdbc.queryForList("""
        select id, type, content, options, answer, explanation, difficulty
          from question
         where unit_id = ? and status = 'ON'
         order by id asc
         limit %d
        """.formatted(MAX_QUESTIONS_PER_PAGE), unitId);
  }

  /** 可见的面包屑：tail 是「上层名称 / 当前名称」交替的字符串 */
  private String crumb(String parentPath, String parentName, String currentName) {
    return "<div class=\"crumb\"><a href=\"/\">首页</a> / <a href=\"" + parentPath + "\">"
        + SeoHtml.escape(parentName) + "</a> / " + SeoHtml.escape(currentName) + "</div>";
  }

  private String breadcrumbLd(List<Map<String, String>> trail) {
    List<Object> items = new ArrayList<>();
    int position = 1;
    for (Map<String, String> node : trail) {
      items.add(Map.of(
          "@type", "ListItem",
          "position", position++,
          "name", node.get("name"),
          "item", seo.absolute(node.get("path"))));
    }
    return jsonLd(Map.of(
        "@context", "https://schema.org",
        "@type", "BreadcrumbList",
        "itemListElement", items));
  }

  /** 结构化数据统一从这里出，避免手工拼 JSON 出错；顺手挡住 </script> 逃逸 */
  private String jsonLd(Object node) {
    try {
      String raw = json.objectMapper().writeValueAsString(node);
      return "<script type=\"application/ld+json\">" + raw.replace("<", "\\u003c")
          + "</script>";
    } catch (Exception e) {
      return "";
    }
  }

  private String entry(String path, String priority, String changefreq, String lastmod) {
    StringBuilder entry = new StringBuilder("  <url><loc>")
        .append(SeoHtml.escape(seo.absolute(path))).append("</loc>");
    if (lastmod != null && !lastmod.isBlank()) {
      entry.append("<lastmod>").append(SeoHtml.escape(lastmod)).append("</lastmod>");
    }
    entry.append("<changefreq>").append(changefreq).append("</changefreq>")
        .append("<priority>").append(priority).append("</priority></url>\n");
    return entry.toString();
  }

  /** Timestamp → 带 Z 的 ISO 字符串，sitemap 的 lastmod 用这个格式 */
  private String lastmod(Object value) {
    Object iso = json.iso(value);
    return iso == null ? "" : String.valueOf(iso);
  }

  private ResponseEntity<String> page(SeoHtml.Page page) {
    return ResponseEntity.ok()
        .header("Content-Type", HTML)
        .header("Cache-Control", CACHE_HTML)
        .body(html.render(page));
  }

  /** 题库不存在或不是公共题库：给一个像样的 404，别让蜘蛛收到 JSON */
  private ResponseEntity<String> notFound() {
    String body = """
        <h1>页面不存在</h1>
        <p class="lead">这个题库可能已经下线，或者它不是你公开的题库。</p>
        <a class="cta" href="/">回到首页</a>
        """;
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .header("Content-Type", HTML)
        // 404 不缓存：题库之后可能被开放，别让边缘把 404 记住
        .header("Cache-Control", "no-store")
        .body(html.render(new SeoHtml.Page(
            "页面不存在 - " + seo.siteName(), "页面不存在。", "/", body)));
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** 题干摘要：标题和列表里用，去掉换行、超长截断 */
  private static String summary(String text, int max) {
    String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
    return flat.length() <= max ? flat : flat.substring(0, max) + "…";
  }

  private static String typeLabel(String type) {
    return switch (type == null ? "" : type) {
      case "SINGLE" -> "单选题";
      case "MULTI" -> "多选题";
      case "SHORT" -> "简答题";
      default -> "题目";
    };
  }

  private static String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  /** MySQL 的 json 列读出来是字符串，交给 Json 统一解析 */
  private List<?> asList(Object value) {
    if (value == null) {
      return List.of();
    }
    Object parsed = json.parse(String.valueOf(value));
    return parsed instanceof List<?> list ? list : List.of();
  }
}
