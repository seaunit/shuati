package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BankWordExportTest {

  private static final MediaType DOCX = MediaType.parseMediaType(
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  @Autowired BankManageController controller;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ownerGetsAValidAutoFormattedWordDocumentWithAllQuestionDetails() throws Exception {
    String userId = createUser();
    long bankId = createBank(userId, "JAVA八股：核心题库");
    long basics = createUnit(bankId, "第一章 Java 基础", 0);
    long advanced = createUnit(bankId, "第二章 JVM 与并发", 1);
    insertChoice(
        basics,
        "SINGLE",
        "**JVM** 默认的垃圾收集器是哪一项？",
        """
            [{"key":"A","text":"Serial GC"},{"key":"B","text":"G1 GC"}]
            """,
        "B",
        "EASY",
        "现代 JDK 默认通常使用 G1。\n## 延伸\n- 对比 Serial GC");
    insertChoice(
        advanced,
        "MULTI",
        "以下哪些属于线程安全的集合？",
        """
            [{"key":"A","text":"ConcurrentHashMap"},{"key":"B","text":"HashMap"},
             {"key":"C","text":"CopyOnWriteArrayList"}]
            """,
        "AC",
        "MEDIUM",
        "`ConcurrentHashMap` 和 `CopyOnWriteArrayList` 是线程安全实现。");
    insertShort(advanced, "简述类加载过程。", "加载、验证、准备、解析、初始化。", "HARD");
    insertChoice(
        basics,
        "TRUE_FALSE",
        "Spring Bean 默认是单例。",
        """
            [{"key":"T","text":"正确"},{"key":"F","text":"错误"}]
            """,
        "T",
        "EASY",
        "Spring 单例作用域是默认值。");
    authenticate(userId, false);

    ResponseEntity<byte[]> response = controller.exportWord(bankId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getHeaders().getContentType()).isEqualTo(DOCX);
    assertThat(String.valueOf(response.getHeaders().getContentDisposition()))
        .contains("attachment")
        .contains(".docx");
    assertThat(response.getBody()).isNotNull().startsWith((byte) 'P', (byte) 'K');

    try (XWPFDocument document = new XWPFDocument(
        new ByteArrayInputStream(response.getBody()))) {
      String text = document.getParagraphs().stream()
          .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
          .reduce("", (left, right) -> left + "\n" + right);

      assertThat(text).contains("JAVA八股：核心题库");
      assertThat(text).contains("第一章 Java 基础");
      assertThat(text).contains("第二章 JVM 与并发");
      assertThat(text).contains("JVM 默认的垃圾收集器是哪一项？");
      assertThat(text).contains("A. Serial GC");
      assertThat(text).contains("B. G1 GC");
      assertThat(text).contains("答案：B");
      assertThat(text).contains("解析：");
      assertThat(text).contains("简述类加载过程。");
      assertThat(text).contains("加载、验证、准备、解析、初始化。");
      assertThat(text).contains("判断题");
      assertThat(text).contains("Spring Bean 默认是单例。");
      assertThat(text).contains("答案：正确");
      assertThat(text).doesNotContain("**");
      assertThat(text).doesNotContain("##");
      assertThat(text).contains("• 对比 Serial GC");

      var pageSize = document.getDocument().getBody().getSectPr().getPgSz();
      assertThat(((Number) pageSize.getW()).intValue()).isEqualTo(11906);
      assertThat(((Number) pageSize.getH()).intValue()).isEqualTo(16838);

      assertThat(document.getParagraphs()).anySatisfy(paragraph -> {
        assertThat(paragraph.getRuns()).isNotEmpty();
        assertThat(paragraph.getRuns().get(0).getFontFamily()).isNotBlank();
      });
    }
  }

  @Test
  void emptyBankStillProducesAReadableDocument() throws Exception {
    String userId = createUser();
    long bankId = createBank(userId, "空题库");
    createUnit(bankId, "第一章", 0);
    authenticate(userId, false);

    ResponseEntity<byte[]> response = controller.exportWord(bankId);

    try (XWPFDocument document = new XWPFDocument(
        new ByteArrayInputStream(response.getBody()))) {
      String text = document.getParagraphs().stream()
          .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
          .reduce("", (left, right) -> left + "\n" + right);
      assertThat(text).contains("空题库");
      assertThat(text).contains("暂无题目");
    }
  }

  @Test
  void exportKeepsOwnerOtherAndAdminPermissionMatrix() {
    String ownerId = createUser();
    long privateBankId = createBank(ownerId, "私有题库");
    long publicBankId = createBank(null, "公共题库");

    authenticate(ownerId, false);
    assertThat(controller.exportWord(privateBankId).getBody()).isNotEmpty();
    assertThat(assertThrows(ApiException.class,
        () -> controller.exportWord(publicBankId)).getStatus()).isEqualTo(403);

    authenticate(createUser(), false);
    assertThat(assertThrows(ApiException.class,
        () -> controller.exportWord(privateBankId)).getStatus()).isEqualTo(403);

    authenticate(createUser(), true);
    assertThat(controller.exportWord(publicBankId).getBody()).isNotEmpty();
  }

  private void authenticate(String userId, boolean admin) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()));
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "word-export-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createBank(String ownerId, String name) {
    String unique = name + "-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", unique, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, unique);
  }

  private long createUnit(long bankId, String name, int sort) {
    jdbc.update(
        "insert into unit (bank_id, name, sort) values (?, ?, ?)", bankId, name, sort);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private void insertChoice(
      long unitId,
      String type,
      String content,
      String options,
      String answer,
      String difficulty,
      String explanation) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, explanation, status)
        values (?, ?, ?, cast(? as json), ?, null, ?, ?, 'ON')
        """, unitId, type, content, options, answer, difficulty, explanation);
  }

  private void insertShort(
      long unitId, String content, String answer, String difficulty) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, explanation, status)
        values (?, 'SHORT', ?, null, ?, cast('["关键过程"]' as json), ?, ?, 'ON')
        """, unitId, content, answer, difficulty, "按阶段说明即可。");
  }
}
