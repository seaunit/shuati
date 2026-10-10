package com.shuati.importer;

import com.shuati.ai.AiClient;
import com.shuati.ai.PromptTemplates;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

  private static final int CHUNK_SIZE = 5000;
  private static final int MAX_TEXT_LENGTH = 100_000;

  private final JdbcTemplate jdbc;
  private final Json json;
  private final AiClient ai;
  private final PointAccountService points;

  /** 提交检查与落库共用一把锁，避免两个请求同时通过「有没有任务在跑」的检查 */
  private final Object importLock = new Object();

  /**
   * 服务重启后，之前处于 PENDING / RUNNING 的任务已经不可能继续执行；
   * 不清理的话「全局单任务」会把这些任务当成仍在进行，导致再也提交不了新任务。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void failOrphanedTasksOnStartup() {
    int affected = jdbc.update("""
        update import_task
           set status = 'FAILED', error = '服务重启导致任务中断，请重新提交'
         where status in ('PENDING', 'RUNNING')
        """);
    if (affected > 0) {
      log.warn("启动清理：{} 个解析任务因服务重启被标记为失败", affected);
    }
  }

  /** 全局同时只允许一个解析任务在跑，省服务器资源 */
  private void requireNoRunningTask() {
    Integer running = jdbc.queryForObject("""
        select count(*) from import_task where status in ('PENDING', 'RUNNING')
        """, Integer.class);
    if (running != null && running > 0) {
      throw new ApiException(409, "已有解析任务正在进行中，请等它完成或失败后再提交新任务");
    }
  }

  public Map<String, Object> createTask(String userId, Map<String, Object> body) {
    String kind = String.valueOf(body.getOrDefault("kind", ""));
    if (!List.of("text", "url", "doc").contains(kind)) {
      throw new ApiException(400, "导入类型不正确");
    }
    String bankName = String.valueOf(body.getOrDefault("bankName", "")).trim();
    if (bankName.isEmpty()) {
      throw new ApiException(400, "题库名称必填");
    }
    String description = body.get("bankDescription") == null ? null
        : String.valueOf(body.get("bankDescription")).trim();
    String sourceText;
    String sourceName = null;
    if ("text".equals(kind)) {
      sourceText = String.valueOf(body.getOrDefault("text", ""));
      if (sourceText.isBlank()) {
        throw new ApiException(400, "请粘贴正文内容");
      }
    } else if ("url".equals(kind)) {
      sourceText = String.valueOf(body.getOrDefault("url", "")).trim();
      if (sourceText.isEmpty()) {
        throw new ApiException(400, "请填写网页链接");
      }
    } else {
      sourceText = String.valueOf(body.getOrDefault("base64", ""));
      sourceName = String.valueOf(body.getOrDefault("fileName", "doc"));
      if (sourceText.isEmpty()) {
        throw new ApiException(400, "请上传文档");
      }
    }

    String taskId = UUID.randomUUID().toString();
    synchronized (importLock) {
      requireNoRunningTask();
      jdbc.update("""
          insert into import_task
            (id, user_id, kind, phase, status, bank_name, bank_description, source_text, source_name)
          values (?, ?, ?, 'PARSE', 'PENDING', ?, ?, ?, ?)
          """, taskId, userId, kind, bankName, description, sourceText, sourceName);
    }

    return Map.of("taskId", taskId);
  }

  public List<Map<String, Object>> listTasks(String userId) {
    return json.normalize(jdbc.queryForList("""
        select id, kind, phase, status, progress, total_chunks, done_chunks,
               bank_name, error, created_at, updated_at
          from import_task
         where user_id = ?
         order by created_at desc
         limit 50
        """, userId), java.util.Set.of());
  }

  public Map<String, Object> getTask(String userId, String taskId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select * from import_task where id = ? and user_id = ?
        """, taskId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "任务不存在");
    }
    return json.normalize(rows.get(0), java.util.Set.of("items", "selected_indexes"));
  }

  public void requestImport(String userId, String taskId, List<Integer> selectedIndexes) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, phase from import_task where id = ? and user_id = ?
        """, taskId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "任务不存在");
    }
    jdbc.update("""
        update import_task
           set phase = 'IMPORT', status = 'PENDING', progress = 0,
               selected_indexes = cast(? as json), error = null
         where id = ?
        """, writeJson(selectedIndexes == null ? List.of() : selectedIndexes), taskId);
  }

  /** 由 ImportWorker 在异步线程中调用，处于独立事务之外。 */
  public void runTask(String taskId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select * from import_task where id = ?", taskId);
    if (rows.isEmpty()) {
      return;
    }
    Map<String, Object> task = rows.get(0);
    try {
      if ("PARSE".equals(task.get("phase"))) {
        runParse(task);
      } else if ("IMPORT".equals(task.get("phase"))) {
        runImport(task);
      }
    } catch (Exception e) {
      jdbc.update("update import_task set status = 'FAILED', error = ? where id = ?",
          e.getMessage(), taskId);
    }
  }

  private void runParse(Map<String, Object> task) {
    String taskId = String.valueOf(task.get("id"));
    String userId = String.valueOf(task.get("user_id"));
    jdbc.update("update import_task set status = 'RUNNING', progress = 0, error = null where id = ?",
        taskId);

    String text;
    try {
      text = sourceTextOf(task);
    } catch (Exception e) {
      throw new ApiException(400, "文档解析失败：" + e.getMessage());
    }
    String capped = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    List<String> chunks = splitText(capped);
    if (chunks.isEmpty()) {
      throw new ApiException(400, "未提取到可解析的文本内容");
    }

    Map<String, Object> charged = points.consumePoints(
        userId, "EXTRACT", capped.length(), "import_task", taskId);
    if (!Boolean.TRUE.equals(charged.get("ok"))) {
      throw new ApiException(402, "AI 点数不足：本次需要 " + charged.get("cost")
          + " 点，当前可用 " + charged.get("available") + " 点。");
    }
    int chargedCost = intValue(charged.get("cost"));

    jdbc.update("update import_task set total_chunks = ?, done_chunks = 0 where id = ?",
        chunks.size(), taskId);

    List<Map<String, Object>> items = new ArrayList<>();
    List<String> chunkErrors = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      try {
        items.addAll(extractChunk(chunks.get(i), userId));
      } catch (Exception e) {
        chunkErrors.add("第 " + (i + 1) + " 块：" + e.getMessage());
      }
      int done = i + 1;
      jdbc.update("update import_task set done_chunks = ?, progress = ? where id = ?",
          done, Math.round(done * 100f / chunks.size()), taskId);
    }

    jdbc.update("""
        update import_task
           set items = cast(? as json), progress = 100, phase = 'READY', status = ?, error = ?
         where id = ?
        """, writeJson(items), chunkErrors.isEmpty() ? "COMPLETED" : "FAILED",
        chunkErrors.isEmpty() ? null : String.join("\n", chunkErrors), taskId);

    if (!chunkErrors.isEmpty() && chargedCost > 0) {
      int refund = (int) Math.floor(chargedCost * (double) chunkErrors.size() / chunks.size());
      if (refund > 0) {
        points.refundPoints(userId, refund, "AI_EXTRACT_PARTIAL_FAILED", "import_task", taskId);
      }
    }
  }

  private void runImport(Map<String, Object> task) {
    String taskId = String.valueOf(task.get("id"));
    String userId = String.valueOf(task.get("user_id"));
    jdbc.update("update import_task set status = 'RUNNING', progress = 0, error = null where id = ?",
        taskId);

    List<Map<String, Object>> items = toItems(task.get("items"));
    List<Integer> selected = toSelected(task.get("selected_indexes"));
    List<Map<String, Object>> chosen = selected.isEmpty() ? items
        : selected.stream().filter(i -> i >= 0 && i < items.size()).map(items::get).toList();
    if (chosen.isEmpty()) {
      throw new ApiException(400, "没有可导入的题目");
    }

    Map<String, Object> entitlements = points.entitlements(userId);
    points.assertBankQuota(userId, entitlements);
    points.assertQuestionQuota(userId, entitlements, chosen.size());

    String bankName = String.valueOf(task.get("bank_name")).trim();
    String description = task.get("bank_description") == null ? null
        : String.valueOf(task.get("bank_description"));

    Long existing = jdbc.queryForList(
        "select id from bank where owner_id = ? and name = ?", Long.class, userId, bankName)
        .stream().findFirst().orElse(null);
    long bankId;
    if (existing != null) {
      bankId = existing;
    } else {
      Map<String, Object> key = new LinkedHashMap<>();
      org.springframework.jdbc.support.KeyHolder keyHolder =
          new org.springframework.jdbc.support.GeneratedKeyHolder();
      jdbc.update(connection -> {
        var ps = connection.prepareStatement(
            "insert into bank (name, description, is_default, owner_id) values (?, ?, 0, ?)",
            new String[]{"id"});
        ps.setString(1, bankName);
        ps.setString(2, description);
        ps.setString(3, userId);
        return ps;
      }, keyHolder);
      bankId = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    Map<String, Long> unitMap = new LinkedHashMap<>();
    for (Map<String, Object> item : chosen) {
      String unitName = String.valueOf(item.getOrDefault("unit", "未分类")).trim();
      if (unitName.isEmpty()) {
        unitName = "未分类";
      }
      if (!unitMap.containsKey(unitName)) {
        List<Long> ids = jdbc.queryForList(
            "select id from unit where bank_id = ? and name = ?", Long.class, bankId, unitName);
        if (!ids.isEmpty()) {
          unitMap.put(unitName, ids.get(0));
        } else {
          org.springframework.jdbc.support.KeyHolder keyHolder =
              new org.springframework.jdbc.support.GeneratedKeyHolder();
          String finalUnitName = unitName;
          long finalBankId = bankId;
          Integer sort = unitMap.size();
          jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                "insert into unit (bank_id, name, sort) values (?, ?, ?)", new String[]{"id"});
            ps.setLong(1, finalBankId);
            ps.setString(2, finalUnitName);
            ps.setInt(3, sort);
            return ps;
          }, keyHolder);
          unitMap.put(unitName, keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue());
        }
      }
    }

    for (Map<String, Object> item : chosen) {
      String unitName = String.valueOf(item.getOrDefault("unit", "未分类")).trim();
      Long unitId = unitMap.get(unitName.isEmpty() ? "未分类" : unitName);
      if (unitId == null) {
        continue;
      }
      jdbc.update("""
          insert into question
            (unit_id, type, content, options, answer, key_points, difficulty,
             explanation, source_url, status)
          values (?, ?, ?, cast(? as json), ?, cast(? as json), ?, ?, ?, 'ON')
          """,
          unitId,
          String.valueOf(item.getOrDefault("type", "SINGLE")),
          String.valueOf(item.getOrDefault("content", "")),
          item.get("options") == null ? null : writeJson(item.get("options")),
          String.valueOf(item.getOrDefault("answer", "")),
          item.get("key_points") == null ? null : writeJson(item.get("key_points")),
          normalizeDifficulty(item.get("difficulty")),
          item.get("explanation") == null ? null : String.valueOf(item.get("explanation")),
          item.get("source_url") == null ? null : String.valueOf(item.get("source_url")));
    }

    jdbc.update("""
        update import_task
           set bank_id = ?, progress = 100, phase = 'DONE', status = 'COMPLETED'
         where id = ?
        """, bankId, taskId);
  }

  private List<Map<String, Object>> extractChunk(String chunk, String userId) throws Exception {
    AiClient.AiResult result = ai.chat("EXTRACT", PromptTemplates.EXTRACT_SYSTEM, chunk, true);
    ai.logUsage(userId, "EXTRACT", result.model(), null, result, null, 0);
    JsonNode root = ai.objectMapper().readTree(result.content());
    JsonNode questions = root.path("questions");
    List<Map<String, Object>> items = new ArrayList<>();
    if (questions.isArray()) {
      for (JsonNode node : questions) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("unit", node.path("unit").asText("未分类"));
        item.put("type", node.path("type").asText("SINGLE"));
        item.put("content", node.path("content").asText(""));
        if (node.has("options") && node.get("options").isArray()) {
          item.put("options", ai.objectMapper().convertValue(node.get("options"), List.class));
        }
        item.put("answer", node.path("answer").asText(""));
        if (node.has("key_points") && node.get("key_points").isArray()) {
          item.put("key_points", ai.objectMapper().convertValue(node.get("key_points"), List.class));
        }
        item.put("difficulty", node.path("difficulty").asText("MEDIUM"));
        if (node.has("explanation")) {
          item.put("explanation", node.path("explanation").asText(""));
        }
        items.add(item);
      }
    }
    return items;
  }

  private String sourceTextOf(Map<String, Object> task) throws Exception {
    String kind = String.valueOf(task.get("kind"));
    String source = task.get("source_text") == null ? "" : String.valueOf(task.get("source_text"));
    if ("text".equals(kind)) {
      return source;
    }
    if ("url".equals(kind)) {
      var response = java.net.http.HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(20)).build()
          .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(source))
                  .header("User-Agent", "Mozilla/5.0")
                  .timeout(Duration.ofSeconds(20)).GET().build(),
              java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new ApiException(400, "抓取网页失败（HTTP " + response.statusCode() + "）");
      }
      return htmlToText(response.body());
    }
    byte[] bytes = Base64.getDecoder().decode(source);
    String name = String.valueOf(task.getOrDefault("source_name", "")).toLowerCase(Locale.ROOT);
    if (name.endsWith(".pdf")) {
      try (PDDocument document = Loader.loadPDF(bytes)) {
        return new PDFTextStripper().getText(document);
      }
    }
    if (name.endsWith(".docx")) {
      try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
           XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
        return extractor.getText();
      }
    }
    if (name.endsWith(".doc")) {
      try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
           WordExtractor extractor = new WordExtractor(document)) {
        return extractor.getText();
      }
    }
    if (name.endsWith(".pptx")) {
      try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
        StringBuilder builder = new StringBuilder();
        for (XSLFSlide slide : slideShow.getSlides()) {
          for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
              builder.append(textShape.getText()).append('\n');
            }
          }
        }
        return builder.toString();
      }
    }
    throw new ApiException(400, "暂不支持该文件类型：" + name);
  }

  public static String htmlToText(String html) {
    String text = Jsoup.parse(html == null ? "" : html).text();
    return text.replaceAll("\n{3,}", "\n\n").trim();
  }

  public static List<String> splitText(String text) {
    String value = text == null ? "" : text.trim();
    if (value.isEmpty()) {
      return List.of();
    }
    if (value.length() <= CHUNK_SIZE) {
      return List.of(value);
    }
    List<String> chunks = new ArrayList<>();
    String[] blocks = value.split("\n\\s*\n");
    StringBuilder buffer = new StringBuilder();
    for (String block : blocks) {
      if (block.isBlank()) {
        continue;
      }
      while (block.length() > CHUNK_SIZE) {
        if (!buffer.isEmpty()) {
          chunks.add(buffer.toString().trim());
          buffer.setLength(0);
        }
        chunks.add(block.substring(0, CHUNK_SIZE).trim());
        block = block.substring(CHUNK_SIZE);
      }
      if (buffer.length() + block.length() > CHUNK_SIZE && !buffer.isEmpty()) {
        chunks.add(buffer.toString().trim());
        buffer.setLength(0);
      }
      buffer.append(block).append("\n\n");
    }
    if (!buffer.isEmpty()) {
      chunks.add(buffer.toString().trim());
    }
    return chunks.stream().filter(c -> !c.isEmpty()).toList();
  }

  private List<Map<String, Object>> toItems(Object value) {
    if (value == null) {
      return List.of();
    }
    Object parsed = value instanceof String text ? json.parse(text) : value;
    if (parsed instanceof List<?> list) {
      List<Map<String, Object>> items = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          Map<String, Object> converted = new LinkedHashMap<>();
          map.forEach((k, v) -> converted.put(String.valueOf(k), v));
          items.add(converted);
        }
      }
      return items;
    }
    return List.of();
  }

  private List<Integer> toSelected(Object value) {
    if (value == null) {
      return List.of();
    }
    Object parsed = value instanceof String text ? json.parse(text) : value;
    if (parsed instanceof List<?> list) {
      List<Integer> indexes = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Number number) {
          indexes.add(number.intValue());
        }
      }
      return indexes;
    }
    return List.of();
  }

  private String normalizeDifficulty(Object value) {
    String difficulty = value == null ? "MEDIUM" : String.valueOf(value).toUpperCase(Locale.ROOT);
    return List.of("EASY", "MEDIUM", "HARD").contains(difficulty) ? difficulty : "MEDIUM";
  }

  private String writeJson(Object value) {
    try {
      return ai.objectMapper().writeValueAsString(value);
    } catch (Exception e) {
      return "null";
    }
  }

  private int intValue(Object value) {
    if (value instanceof Boolean bool) {
      return bool ? 1 : 0;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }
}

