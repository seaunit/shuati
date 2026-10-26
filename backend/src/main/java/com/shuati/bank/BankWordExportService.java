package com.shuati.bank;

import com.shuati.common.Json;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankWordExportService {

  private static final String BODY_FONT = "宋体";
  private static final String HEADING_FONT = "微软雅黑";
  private static final String MONO_FONT = "Consolas";
  private static final String INK = "252A31";
  private static final String MUTED = "68707C";
  private static final String MOSS = "3F6B57";
  private static final BigInteger A4_WIDTH = BigInteger.valueOf(11906);
  private static final BigInteger A4_HEIGHT = BigInteger.valueOf(16838);
  private static final BigInteger PAGE_MARGIN = BigInteger.valueOf(1134);
  private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter
      .ofPattern("yyyy-MM-dd HH:mm")
      .withZone(ZoneId.of("Asia/Shanghai"));

  private final JdbcTemplate jdbc;
  private final Json json;

  public ExportFile export(long bankId) {
    Map<String, Object> bank = jdbc.queryForMap(
        "select name, description from bank where id = ?", bankId);
    List<Map<String, Object>> units = jdbc.queryForList("""
        select id, name, sort
          from unit
         where bank_id = ?
         order by sort asc, id asc
        """, bankId);
    List<Map<String, Object>> questions = jdbc.queryForList("""
        select q.id, q.unit_id, q.type, q.content, q.options, q.answer,
               q.key_points, q.difficulty, q.explanation, q.tags, q.status
          from question q
          join unit u on u.id = q.unit_id
         where u.bank_id = ?
         order by u.sort asc, u.id asc, q.id asc
        """, bankId);

    Map<Long, List<Map<String, Object>>> questionsByUnit = new LinkedHashMap<>();
    for (Map<String, Object> question : questions) {
      long unitId = ((Number) question.get("unit_id")).longValue();
      questionsByUnit.computeIfAbsent(unitId, ignored -> new ArrayList<>()).add(question);
    }

    byte[] content = render(bank, units, questions, questionsByUnit);
    return new ExportFile(fileName(String.valueOf(bank.get("name"))), content);
  }

  private byte[] render(
      Map<String, Object> bank,
      List<Map<String, Object>> units,
      List<Map<String, Object>> questions,
      Map<Long, List<Map<String, Object>>> questionsByUnit) {
    try (XWPFDocument document = new XWPFDocument();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      configurePage(document);
      configureProperties(document, bank);
      addCover(document, bank, units.size(), questions.size());

      if (questions.isEmpty()) {
        addParagraph(document, "暂无题目", 12, false, MUTED, HEADING_FONT,
            ParagraphAlignment.CENTER, 360, 360, 0);
      } else {
        int questionNumber = 1;
        for (Map<String, Object> unit : units) {
          long unitId = ((Number) unit.get("id")).longValue();
          List<Map<String, Object>> unitQuestions = questionsByUnit.getOrDefault(
              unitId, List.of());
          if (unitQuestions.isEmpty()) {
            continue;
          }
          addUnitTitle(document, String.valueOf(unit.get("name")), unitQuestions.size());
          for (Map<String, Object> question : unitQuestions) {
            addQuestion(document, question, questionNumber++);
          }
        }
      }

      document.write(output);
      return output.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("生成 Word 文档失败", e);
    }
  }

  private void configurePage(XWPFDocument document) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr()
        : document.getDocument().getBody().addNewSectPr();
    CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    size.setW(A4_WIDTH);
    size.setH(A4_HEIGHT);

    CTPageMar margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margin.setTop(PAGE_MARGIN);
    margin.setBottom(PAGE_MARGIN);
    margin.setLeft(PAGE_MARGIN);
    margin.setRight(PAGE_MARGIN);
    margin.setHeader(BigInteger.valueOf(567));
    margin.setFooter(BigInteger.valueOf(567));
    margin.setGutter(BigInteger.ZERO);
  }

  private void configureProperties(XWPFDocument document, Map<String, Object> bank) {
    String name = String.valueOf(bank.get("name"));
    document.getProperties().getCoreProperties().setTitle(name);
    document.getProperties().getCoreProperties().setCreator("拾题");
    document.getProperties().getCoreProperties().setDescription("拾题题库 Word 导出");
  }

  private void addCover(
      XWPFDocument document,
      Map<String, Object> bank,
      int unitCount,
      int questionCount) {
    addParagraph(document, "拾题 · 题库资料", 10, true, MOSS, HEADING_FONT,
        ParagraphAlignment.CENTER, 0, 120, 0);
    addParagraph(document, String.valueOf(bank.get("name")), 22, true, INK, HEADING_FONT,
        ParagraphAlignment.CENTER, 80, 120, 0);

    String description = string(bank.get("description"));
    if (!description.isBlank()) {
      addParagraph(document, stripInlineMarkdown(description), 10.5, false, MUTED, BODY_FONT,
          ParagraphAlignment.CENTER, 0, 100, 0);
    }

    String meta = "共 %d 个单元 · %d 道题 · 导出于 %s"
        .formatted(unitCount, questionCount, EXPORT_TIME.format(Instant.now()));
    addParagraph(
        document, meta, 9, false, MUTED, BODY_FONT,
        ParagraphAlignment.CENTER, 0, 260, 0);
  }

  private void addUnitTitle(XWPFDocument document, String name, int questionCount) {
    XWPFParagraph paragraph = addParagraph(
        document, name, 15, true, INK, HEADING_FONT,
        ParagraphAlignment.LEFT, 280, 50, 0);
    paragraph.setKeepNext(true);
    XWPFParagraph count = addParagraph(
        document, "本单元 %d 题".formatted(questionCount), 9, false, MUTED, BODY_FONT,
        ParagraphAlignment.LEFT, 0, 120, 0);
    count.setKeepNext(true);
  }

  private void addQuestion(
      XWPFDocument document, Map<String, Object> question, int questionNumber) {
    XWPFParagraph heading = document.createParagraph();
    heading.setSpacingBefore(170);
    heading.setSpacingAfter(70);
    heading.setKeepNext(true);
    addRun(heading, questionNumber + ". ", 11.5, true, INK, HEADING_FONT);

    String type = typeLabel(string(question.get("type")));
    String difficulty = difficultyLabel(string(question.get("difficulty")));
    String status = "OFF".equals(question.get("status")) ? " · 已下架" : "";
    addRun(heading, "［%s · %s%s］".formatted(type, difficulty, status),
        9, false, MUTED, BODY_FONT);

    appendMarkdown(document, string(question.get("content")), 11);

    List<Map<String, Object>> options = optionList(question.get("options"));
    for (Map<String, Object> option : options) {
      String key = string(option.get("key"));
      String text = stripInlineMarkdown(string(option.get("text")));
      if (key.isBlank() && text.isBlank()) {
        continue;
      }
      XWPFParagraph optionParagraph = addParagraph(
          document,
          (key.isBlank() ? "" : key + ". ") + text,
          10.5,
          false,
          INK,
          BODY_FONT,
          ParagraphAlignment.LEFT,
          20,
          45,
          420);
      optionParagraph.setKeepNext(true);
    }

    addLabeledBlock(document, "答案：", string(question.get("answer")), false);

    List<Object> keyPoints = objectList(question.get("key_points"));
    if (!keyPoints.isEmpty()) {
      addLabeledBlock(
          document,
          "评分要点：",
          keyPoints.stream().map(String::valueOf).reduce((left, right) -> left + "；" + right)
              .orElse(""),
          true);
    }

    String explanation = string(question.get("explanation"));
    if (!explanation.isBlank()) {
      addLabeledBlock(document, "解析：", explanation, false);
    }
  }

  private void addLabeledBlock(
      XWPFDocument document, String label, String content, boolean muted) {
    if (content == null || content.isBlank()) {
      return;
    }
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setSpacingBefore(90);
    paragraph.setSpacingAfter(30);
    paragraph.setIndentationLeft(240);
    addRun(paragraph, label, 10, true, muted ? MUTED : INK, HEADING_FONT);
    appendTextLines(paragraph, stripInlineMarkdown(content), 10, muted ? MUTED : INK, BODY_FONT);
  }

  private void appendMarkdown(XWPFDocument document, String value, double size) {
    boolean inCode = false;
    String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    for (String rawLine : lines) {
      String line = rawLine.stripTrailing();
      if (line.trim().startsWith("```")) {
        inCode = !inCode;
        continue;
      }
      if (inCode) {
        addParagraph(document, line, size - 1, false, INK, MONO_FONT,
            ParagraphAlignment.LEFT, 20, 20, 240);
        continue;
      }
      if (line.isBlank()) {
        continue;
      }
      String trimmed = line.trim();
      if (trimmed.matches("^#{1,6}\\s+.*")) {
        String heading = trimmed.replaceFirst("^#{1,6}\\s+", "");
        addParagraph(document, stripInlineMarkdown(heading), size + 1, true, INK, HEADING_FONT,
            ParagraphAlignment.LEFT, 80, 40, 0);
      } else if (trimmed.matches("^[-*+]\\s+.*")) {
        String bullet = trimmed.replaceFirst("^[-*+]\\s+", "");
        addParagraph(document, "• " + stripInlineMarkdown(bullet), size, false, INK, BODY_FONT,
            ParagraphAlignment.LEFT, 15, 30, 280);
      } else {
        addParagraph(document, stripInlineMarkdown(line), size, false, INK, BODY_FONT,
            ParagraphAlignment.LEFT, 15, 35, 0);
      }
    }
  }

  private XWPFParagraph addParagraph(
      XWPFDocument document,
      String text,
      double size,
      boolean bold,
      String color,
      String font,
      ParagraphAlignment alignment,
      int spacingBefore,
      int spacingAfter,
      int indentLeft) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setAlignment(alignment);
    paragraph.setSpacingBefore(spacingBefore);
    paragraph.setSpacingAfter(spacingAfter);
    paragraph.setSpacingBetween(1.35);
    paragraph.setIndentationLeft(indentLeft);
    addRun(paragraph, text, size, bold, color, font);
    return paragraph;
  }

  private void appendTextLines(
      XWPFParagraph paragraph, String value, double size, String color, String font) {
    String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].trim().startsWith("```")) {
        continue;
      }
      if (i > 0) {
        paragraph.createRun().addBreak();
      }
      String line = lines[i]
          .replaceFirst("^\\s*#{1,6}\\s+", "")
          .replaceFirst("^\\s*[-*+]\\s+", "• ");
      addRun(paragraph, line, size, false, color, font);
    }
  }

  private void addRun(
      XWPFParagraph paragraph,
      String text,
      double size,
      boolean bold,
      String color,
      String font) {
    XWPFRun run = paragraph.createRun();
    run.setText(text == null ? "" : text);
    run.setFontSize(size);
    run.setBold(bold);
    run.setColor(color);
    setFontFamily(run, font);
  }

  private void setFontFamily(XWPFRun run, String family) {
    CTRPr properties = run.getCTR().isSetRPr()
        ? run.getCTR().getRPr()
        : run.getCTR().addNewRPr();
    CTFonts fonts = properties.addNewRFonts();
    fonts.setAscii(family);
    fonts.setHAnsi(family);
    fonts.setEastAsia(family);
    fonts.setCs(family);
  }

  private List<Map<String, Object>> optionList(Object raw) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : objectList(raw)) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        result.add(normalized);
      }
    }
    return result;
  }

  private List<Object> objectList(Object raw) {
    Object parsed = raw instanceof String text ? json.parse(text) : raw;
    return parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
  }

  private String stripInlineMarkdown(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "$1（$2）")
        .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1（$2）")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "");
  }

  private String typeLabel(String type) {
    return switch (type) {
      case "SINGLE" -> "单选题";
      case "MULTI" -> "多选题";
      case "SHORT" -> "简答题";
      default -> type;
    };
  }

  private String difficultyLabel(String difficulty) {
    return switch (difficulty.toUpperCase(Locale.ROOT)) {
      case "EASY" -> "简单";
      case "HARD" -> "困难";
      default -> "中等";
    };
  }

  private String fileName(String bankName) {
    String safe = bankName
        .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
        .replaceAll("\\s+", " ")
        .trim();
    if (safe.isBlank()) {
      safe = "题库";
    }
    if (safe.length() > 80) {
      safe = safe.substring(0, 80);
    }
    return safe + ".docx";
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  public record ExportFile(String fileName, byte[] content) {
  }
}
