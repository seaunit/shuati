package com.shuati.seo;

import com.shuati.common.Json;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向搜索引擎的公开页面（百度蜘蛛不执行 JS，所以这些页面直接吐 HTML）。
 *
 * <p>只暴露公共题库（owner_id 为空）：用户自己建的题库是私有的，不参与收录。
 * 页面里除了正文，还给出 canonical 和指向下一层的链接，方便蜘蛛顺着爬。
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

  private static final String HTML = "text/html;charset=UTF-8";
  private static final String XML = "application/xml;charset=UTF-8";
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
                 where u2.bank_id = b.id) as question_count
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
            .append("<span class=\"meta\">").append(number(bank.get("unit_count"))).append(" 个单元 · ")
            .append(number(bank.get("question_count"))).append(" 道题</span></li>");
      }
      body.append("</ul>");
    }

    body.append("<a class=\"cta\" href=\"/app\">进入题库开始刷题</a>");
    return page(new SeoHtml.Page(
        seo.siteName() + " - " + seo.tagline(), seo.description(), "/", body.toString()));
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
               (select count(*) from question q where q.unit_id = u.id) as question_count
          from unit u
         where u.bank_id = ?
         order by u.sort asc, u.id asc
        """, bankId);

    long total = units.stream().mapToLong(unit -> number(unit.get("question_count"))).sum();

    StringBuilder body = new StringBuilder();
    body.append("<div class=\"crumb\"><a href=\"/\">首页</a> / ")
        .append(SeoHtml.escape(bankName)).append("</div>");
    body.append("<h1>").append(SeoHtml.escape(bankName)).append(" · 题目与答案</h1>");
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

    return page(new SeoHtml.Page(
        bankName + " - 题目与答案 - " + seo.siteName(),
        bankName + "共 " + total + " 道题，覆盖 " + units.size() + " 个单元，含答案与解析。",
        "/bank/" + bankId,
        body.toString()));
  }

  /** 单元页：把单元里的题目、选项、答案、解析整段输出，这是收录的主力页面 */
  @GetMapping(value = "/bank/{bankId}/unit/{unitId}", produces = HTML)
  public ResponseEntity<String> unit(@PathVariable long bankId, @PathVariable long unitId) {
    List<Map<String, Object>> units = jdbc.queryForList("""
        select u.id, u.name, b.name as bank_name
          from unit u join bank b on b.id = u.bank_id
         where u.id = ? and u.bank_id = ? and b.owner_id is null
        """, unitId, bankId);
    if (units.isEmpty()) {
      return notFound();
    }
    Map<String, Object> unit = units.get(0);
    String unitName = string(unit.get("name"));
    String bankName = string(unit.get("bank_name"));

    List<Map<String, Object>> questions = jdbc.queryForList("""
        select id, type, content, options, answer, explanation, difficulty
          from question
         where unit_id = ? and status = 'ON'
         order by id asc
         limit %d
        """.formatted(MAX_QUESTIONS_PER_PAGE), unitId);

    StringBuilder body = new StringBuilder();
    body.append("<div class=\"crumb\"><a href=\"/\">首页</a> / <a href=\"/bank/").append(bankId)
        .append("\">").append(SeoHtml.escape(bankName)).append("</a> / ")
        .append(SeoHtml.escape(unitName)).append("</div>");
    body.append("<h1>").append(SeoHtml.escape(unitName)).append(" - ")
        .append(SeoHtml.escape(bankName)).append("题目与答案</h1>");
    body.append("<p class=\"lead\">本单元共 ").append(questions.size())
        .append(" 道题，每题都给答案，部分带解析。</p>");

    if (questions.isEmpty()) {
      body.append("<p class=\"lead\">这个单元还没有上线的题目。</p>");
    }
    int index = 1;
    for (Map<String, Object> question : questions) {
      String type = string(question.get("type"));
      body.append("<div class=\"card\">");
      body.append("<h3>").append(index++).append(". ")
          .append(typeLabel(type)).append("</h3>");
      body.append(SeoHtml.text(string(question.get("content"))));

      List<?> options = asList(question.get("options"));
      if (!options.isEmpty()) {
        body.append("<div>");
        for (Object option : options) {
          if (option instanceof Map<?, ?> map) {
            body.append("<p class=\"opt\">")
                .append(SeoHtml.escape(string(map.get("key")))).append(". ")
                .append(SeoHtml.escape(string(map.get("text")))).append("</p>");
          }
        }
        body.append("</div>");
      }

      body.append("<div class=\"answer\">答案：")
          .append(SeoHtml.escape(string(question.get("answer")))).append("</div>");
      String explanation = string(question.get("explanation"));
      if (!explanation.isBlank()) {
        body.append("<h3>解析</h3>").append(SeoHtml.text(explanation));
      }
      body.append("</div>");
    }

    body.append("<a class=\"cta\" href=\"/app/practice?unitId=").append(unitId)
        .append("\">开始刷这个单元</a>");

    return page(new SeoHtml.Page(
        unitName + " - " + bankName + "题目与答案 - " + seo.siteName(),
        bankName + unitName + "共 " + questions.size() + " 道题，含答案与解析，可在拾题在线刷题并记录错题。",
        "/bank/" + bankId + "/unit/" + unitId,
        body.toString()));
  }

  /** sitemap：首页 + 合规页 + 所有公共题库与单元 */
  @GetMapping(value = "/sitemap.xml", produces = XML)
  public ResponseEntity<String> sitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    xml.append(entry("/", "1.0", "daily"));
    xml.append(entry("/terms", "0.3", "monthly"));
    xml.append(entry("/privacy", "0.3", "monthly"));

    List<Map<String, Object>> rows = jdbc.queryForList("""
        select b.id as bank_id, u.id as unit_id
          from bank b left join unit u on u.bank_id = b.id
         where b.owner_id is null
         order by b.id asc, u.sort asc, u.id asc
         limit %d
        """.formatted(SITEMAP_LIMIT));
    LinkedHashSet<Long> banks = new LinkedHashSet<>();
    for (Map<String, Object> row : rows) {
      long bankId = number(row.get("bank_id"));
      if (banks.add(bankId)) {
        xml.append(entry("/bank/" + bankId, "0.8", "weekly"));
      }
      if (row.get("unit_id") != null) {
        xml.append(entry("/bank/" + bankId + "/unit/" + number(row.get("unit_id")), "0.7", "weekly"));
      }
    }
    xml.append("</urlset>\n");
    return ResponseEntity.ok().header("Content-Type", XML).body(xml.toString());
  }

  private String entry(String path, String priority, String changefreq) {
    return "  <url><loc>" + SeoHtml.escape(seo.absolute(path)) + "</loc>"
        + "<changefreq>" + changefreq + "</changefreq>"
        + "<priority>" + priority + "</priority></url>\n";
  }

  private ResponseEntity<String> page(SeoHtml.Page page) {
    return ResponseEntity.ok().header("Content-Type", HTML).body(html.render(page));
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
        .body(html.render(new SeoHtml.Page(
            "页面不存在 - " + seo.siteName(), "页面不存在。", "/", body)));
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
