package com.shuati.seo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEO 页面：只暴露公共题库，页面里要有正文、canonical 和往下一层的链接，
 * 私有题库与不存在的 id 一律 404。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class SeoPageTest {

  @Autowired SeoController controller;
  @Autowired JdbcTemplate jdbc;

  @Test
  void homeListsPublicBanksAndLinksToThem() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");
    createQuestion(unitId, "下面哪个是索引的作用？");

    var response = controller.home();
    String body = response.getBody();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("Content-Type")).contains("text/html");
    // 边缘要能缓存直出页面，省掉回源那一段（国内到 CF 边缘本身就不快）
    assertThat(response.getHeaders().getFirst("Cache-Control")).contains("public");
    assertThat(body).contains("<title>");
    assertThat(body).contains("rel=\"canonical\"");
    // 百度统计脚本要出现在 SEO 页面里，否则搜索流量统计不到
    assertThat(body).contains("hm.baidu.com/hm.js?639e5d48916802a7505c8af19d7002e7");
    // 结构化数据：首页给 WebSite + Organization
    assertThat(body).contains("application/ld+json").contains("\"WebSite\"");
    assertThat(body).contains("href=\"/bank/" + bankId + "\"");
    assertThat(body).contains("1 个单元");
  }

  @Test
  void bankPageListsUnitsAndLinksToUnitPages() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章 计算机组成");

    String body = controller.bank(bankId).getBody();

    assertThat(body).contains("第一章 计算机组成");
    assertThat(body).contains("href=\"/bank/" + bankId + "/unit/" + unitId + "\"");
    assertThat(body).contains("canonical\" href=\"https://www.seaunit.site/bank/" + bankId + "\"");
    assertThat(body).contains("\"BreadcrumbList\"");

    // 题库页是头部词的承接页：要有 H1 主题、备考正文、FAQ 与结构化标注、明确的 CTA
    assertThat(body).contains("<h1>").contains("（含答案与解析）</h1>");
    assertThat(body).contains("<h2>怎么用真题备考</h2>");
    assertThat(body).contains("<h2>常见问题</h2>").contains("class=\"faq\"");
    assertThat(body).contains("\"FAQPage\"").contains("\"acceptedAnswer\"");
    assertThat(body).contains("免费开始刷");
  }

  @Test
  void unitPageShowsQuestionsWithAnswers() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");
    long questionId = createQuestion(unitId, "在计算机中，指令寄存器的位数取决于什么？");

    String body = controller.unit(bankId, unitId).getBody();

    assertThat(body).contains("在计算机中，指令寄存器的位数取决于什么？");
    assertThat(body).contains("答案：A");
    assertThat(body).contains("加快查询");
    // 单元页把每道题链到单题详情页
    assertThat(body).contains("href=\"/bank/" + bankId + "/question/" + questionId + "\"");
  }

  @Test
  void questionPageShowsQuestionAnswerAndSiblings() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");
    long first = createQuestion(unitId, "第一题的题干内容？");
    long second = createQuestion(unitId, "第二题的题干内容？");

    String body = controller.question(bankId, first).getBody();

    assertThat(body).contains("第一题的题干内容？");
    assertThat(body).contains("答案：A");
    assertThat(body).contains("加快查询");
    // 结构化数据：单题给 Question/Answer + 面包屑
    assertThat(body).contains("\"Question\"").contains("acceptedAnswer");
    assertThat(body).contains("\"BreadcrumbList\"");
    // 同单元其他题目作为内链
    assertThat(body).contains("href=\"/bank/" + bankId + "/question/" + second + "\"");
    assertThat(body).contains("下一题");
  }

  @Test
  void publicPagesShowTrueFalseLabelAndChineseAnswer() {
    long bankId = createBank(null, "判断题题库" + UUID.randomUUID());
    long unitId = createUnit(bankId, "Spring");
    long questionId = createTrueFalseQuestion(unitId, "Spring Bean 默认是单例。");

    String unit = controller.unit(bankId, unitId).getBody();
    String question = controller.question(bankId, questionId).getBody();

    assertThat(unit)
        .contains("判断题")
        .contains("答案：正确")
        .contains(">T. 正确<")
        .contains(">F. 错误<");
    assertThat(question)
        .contains("判断题")
        .contains("答案：正确")
        .contains("正确答案：正确");
  }

  @Test
  void privateBankAndItsQuestionsAreNotExposed() {
    String ownerId = createUser();
    long bankId = createBank(ownerId, "我的私有题库" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");
    long questionId = createQuestion(unitId, "私有题库里的题");

    assertThat(controller.bank(bankId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.unit(bankId, unitId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.question(bankId, questionId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownBankReturnsHtmlNotFound() {
    var response = controller.bank(999_999_999L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("页面不存在");
    assertThat(response.getHeaders().getFirst("Content-Type")).contains("text/html");
    // 404 不能被边缘缓存住：题库之后可能被开放
    assertThat(response.getHeaders().getFirst("Cache-Control")).contains("no-store");
  }

  @Test
  void sitemapCoversHomeComplianceAndPublicBanks() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");

    String xml = controller.sitemap().getBody();

    assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(xml).contains("<loc>https://www.seaunit.site/</loc>");
    assertThat(xml).contains("<loc>https://www.seaunit.site/terms</loc>");
    assertThat(xml).contains("<loc>https://www.seaunit.site/bank/" + bankId + "</loc>");
    assertThat(xml)
        .contains("<loc>https://www.seaunit.site/bank/" + bankId + "/unit/" + unitId + "</loc>");
    // lastmod 是 Google 真正会用的字段
    assertThat(xml).contains("<lastmod>");
    assertThat(controller.sitemap().getHeaders().getFirst("Cache-Control")).contains("public");
  }

  @Test
  void sitemapSkipsPrivateBanks() {
    long bankId = createBank(createUser(), "私有的" + UUID.randomUUID());

    assertThat(controller.sitemap().getBody()).doesNotContain("/bank/" + bankId + "<");
  }

  @Test
  void sitemapIncludesQuestionPagesForPublicBanks() {
    long bankId = createBank(null, "软考真题" + UUID.randomUUID());
    long unitId = createUnit(bankId, "第一章");
    long questionId = createQuestion(unitId, "在计算机中，指令寄存器的位数取决于什么？");

    assertThat(controller.sitemap().getBody())
        .contains("<loc>https://www.seaunit.site/bank/" + bankId + "/question/" + questionId
            + "</loc>");
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "seo-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createBank(String ownerId, String name) {
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, name);
  }

  private long createUnit(long bankId, String name) {
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 0)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private long createQuestion(long unitId, String content) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', ?,
                cast('[{"key":"A","text":"加快查询"},{"key":"B","text":"加快写入"}]' as json),
                'A', cast('[]' as json), 'MEDIUM', 'ON')
        """, unitId, content);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? and content = ?", Long.class, unitId, content);
  }

  private long createTrueFalseQuestion(long unitId, String content) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'TRUE_FALSE', ?,
                cast('[{"key":"T","text":"正确"},{"key":"F","text":"错误"}]' as json),
                'T', null, 'EASY', 'ON')
        """, unitId, content);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? and content = ?", Long.class, unitId, content);
  }
}
