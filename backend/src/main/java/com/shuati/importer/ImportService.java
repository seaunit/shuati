package com.shuati.importer;

import com.shuati.abuse.NewAccountAiLimit;
import com.shuati.ai.AiCallLogger;
import com.shuati.ai.AiWorkflowInputs;
import com.shuati.ai.AiWorkflowKey;
import com.shuati.ai.AiWorkflowPort;
import com.shuati.ai.AiWorkflowResult;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.Json;
import com.shuati.common.QuestionContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.FileMagic;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

  private static final int CHUNK_SIZE = 5000;
  private static final int MAX_TEXT_LENGTH = 100_000;
  private static final int SOURCE_HISTORY_LIMIT = 10;
  private static final int SUCCESSFUL_IMPORT_HISTORY_LIMIT = 10;

  private final JdbcTemplate jdbc;
  private final Json json;
  private final AiWorkflowPort ai;
  private final AiCallLogger aiCallLogger;
  private final ObjectMapper objectMapper;
  private final NewAccountAiLimit newAccountAiLimit;
  private final PointAccountService points;
  private final RobotsPolicy robotsPolicy;
  private final PlatformTransactionManager transactionManager;

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
    pruneAllSourceHistoriesSafely();
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
    if (!List.of("text", "url", "doc", "screenshot").contains(kind)) {
      throw new ApiException(400, "导入类型不正确");
    }
    String bankName = String.valueOf(body.getOrDefault("bankName", "")).trim();
    // 名称允许留空：用户可能打算把解析结果导入到已有题库里，提交导入时再校验
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
      sourceName = String.valueOf(body.getOrDefault(
          "fileName", "screenshot".equals(kind) ? "screenshot.png" : "doc"));
      if (sourceText.isEmpty()) {
        throw new ApiException(400, "screenshot".equals(kind) ? "请先粘贴或选择截图" : "请上传文档");
      }
      if ("screenshot".equals(kind)) {
        validateScreenshot(sourceText);
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
      pruneSourceHistorySafely(userId);
    }

    return Map.of("taskId", taskId);
  }

  public List<Map<String, Object>> listTasks(String userId) {
    return json.normalize(jdbc.queryForList("""
        select s.id, s.kind, s.phase, s.status, s.progress,
               s.total_chunks, s.done_chunks, s.bank_name, s.error,
               s.created_at, s.updated_at,
               j.id as latest_import_id,
               j.status as latest_import_status,
               j.progress as latest_import_progress,
               j.created_count as latest_created_count,
               j.skipped_count as latest_skipped_count,
               j.target_bank_id as latest_target_bank_id,
               j.bank_id as latest_bank_id,
               j.error as latest_import_error
          from import_task s
          left join import_task j on j.id = (
            select j2.id
              from import_task j2
             where j2.source_task_id = s.id
             order by j2.updated_at desc, j2.created_at desc, j2.id desc
             limit 1
          )
         where s.user_id = ? and s.source_task_id is null
         order by s.created_at desc
         limit ?
        """, userId, SOURCE_HISTORY_LIMIT), java.util.Set.of());
  }

  public Map<String, Object> getTask(String userId, String taskId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, kind, phase, status, progress, total_chunks, done_chunks,
               bank_name, error, items, selected_indexes, created_at, updated_at
          from import_task
         where id = ? and user_id = ? and source_task_id is null
        """, taskId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "任务不存在");
    }
    Map<String, Object> task =
        json.normalize(rows.get(0), java.util.Set.of("items", "selected_indexes"));
    latestImportSummary(userId, taskId).forEach(task::put);
    return task;
  }

  /**
   * 提交导入。
   *
   * @param targetBankId 目标题库；为 null 表示按任务里的名称新建（或复用自己同名的）题库
   */
  public Map<String, Object> requestImport(
      String userId, String taskId, List<Integer> selectedIndexes, Long targetBankId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, kind, bank_name, bank_description, items
          from import_task
         where id = ? and user_id = ? and source_task_id is null
        """, taskId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "任务不存在");
    }
    if (toItems(rows.get(0).get("items")).isEmpty()) {
      throw new ApiException(400, "该记录还没有可导入的解析结果");
    }
    if (targetBankId == null) {
      // 新建题库必须有名字，否则等异步执行时才失败，不如现在就报出来
      String bankName = String.valueOf(rows.get(0).get("bank_name")).trim();
      if (bankName.isEmpty()) {
        throw new ApiException(400, "请先填写题库名称，或选择导入到已有题库");
      }
    } else {
      requireImportAccess(userId, targetBankId);
    }

    String jobId = UUID.randomUUID().toString();
    Map<String, Object> source = rows.get(0);
    synchronized (importLock) {
      requireNoRunningTask();
      jdbc.update("""
          insert into import_task
            (id, user_id, source_task_id, kind, phase, status, progress,
             total_chunks, done_chunks, bank_name, bank_description,
             selected_indexes, target_bank_id)
          values (?, ?, ?, ?, 'IMPORT', 'PENDING', 0, 0, 0, ?, ?, cast(? as json), ?)
          """, jobId, userId, taskId, String.valueOf(source.get("kind")),
          String.valueOf(source.get("bank_name")), source.get("bank_description"),
          writeJson(selectedIndexes == null ? List.of() : selectedIndexes), targetBankId);
    }
    return Map.of("jobId", jobId);
  }

  private Map<String, Object> latestImportSummary(String userId, String sourceTaskId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id as latest_import_id,
               status as latest_import_status,
               progress as latest_import_progress,
               created_count as latest_created_count,
               skipped_count as latest_skipped_count,
               target_bank_id as latest_target_bank_id,
               bank_id as latest_bank_id,
               error as latest_import_error
          from import_task
         where user_id = ? and source_task_id = ?
         order by updated_at desc, created_at desc, id desc
         limit 1
        """, userId, sourceTaskId);
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("latest_import_id", null);
    summary.put("latest_import_status", null);
    summary.put("latest_import_progress", null);
    summary.put("latest_created_count", null);
    summary.put("latest_skipped_count", null);
    summary.put("latest_target_bank_id", null);
    summary.put("latest_bank_id", null);
    summary.put("latest_import_error", null);
    if (!rows.isEmpty()) {
      summary.putAll(rows.get(0));
    }
    return summary;
  }

  /**
   * 目标题库的写入权限：自己的题库可以；公共题库（owner_id 为空）只有管理员可以。
   *
   * <p>这里显式查 profiles.role，而不是用 {@code CurrentUser} —— 导入是在异步线程里执行的，
   * 那条线程上没有登录上下文。
   */
  private void requireImportAccess(String userId, long bankId) {
    List<Map<String, Object>> banks = jdbc.queryForList(
        "select id, owner_id from bank where id = ?", bankId);
    if (banks.isEmpty()) {
      throw new ApiException(404, "目标题库不存在");
    }
    Object ownerId = banks.get(0).get("owner_id");
    if (ownerId == null) {
      if (!isAdmin(userId)) {
        throw new ApiException(403, "公共题库只能由管理员维护，请选择自己的题库或新建题库");
      }
      return;
    }
    if (!String.valueOf(ownerId).equals(userId)) {
      throw new ApiException(403, "无权向该题库导入题目");
    }
  }

  private boolean isAdmin(String userId) {
    List<String> roles = jdbc.queryForList(
        "select role from profiles where id = ?", String.class, userId);
    return !roles.isEmpty() && "ADMIN".equals(roles.get(0));
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
    } catch (Throwable e) {
      // NoSuchMethodError / OOM 这类 Error 也要落库：只 catch Exception 会让任务永远停在 RUNNING
      String message = e.getMessage() == null ? e.toString() : e.getMessage();
      log.error("导入任务执行失败：taskId={}", taskId, e);
      try {
        jdbc.update("update import_task set status = 'FAILED', error = ? where id = ?",
            message, taskId);
      } catch (Exception updateError) {
        log.error("标记导入任务失败时出错：taskId={}", taskId, updateError);
      }
    }
  }

  private void runParse(Map<String, Object> task) {
    String taskId = String.valueOf(task.get("id"));
    String userId = String.valueOf(task.get("user_id"));
    jdbc.update("update import_task set status = 'RUNNING', progress = 0, error = null where id = ?",
        taskId);

    if ("screenshot".equals(String.valueOf(task.get("kind")))) {
      runParseScreenshot(task, taskId, userId);
      return;
    }

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

    Map<String, Object> source = importSourceOf(task, userId);
    List<Map<String, Object>> items = toItems(source.get("items"));
    List<Integer> selected = toSelected(task.get("selected_indexes"));
    List<Map<String, Object>> chosen = selected.isEmpty() ? items
        : selected.stream().filter(i -> i >= 0 && i < items.size()).map(items::get).toList();
    if (chosen.isEmpty()) {
      throw new ApiException(400, "没有可导入的题目");
    }

    Long targetBankId = task.get("target_bank_id") == null ? null
        : ((Number) task.get("target_bank_id")).longValue();
    String bankName = String.valueOf(source.get("bank_name")).trim();
    String description = source.get("bank_description") == null ? null
        : String.valueOf(source.get("bank_description"));
    new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
        runImportInTransaction(
            taskId, userId, chosen, targetBankId, bankName, description));
  }

  /**
   * 解析来源与执行记录分开以后，导入线程要读取来源的 items；
   * 兼容旧数据时仍允许执行记录自己携带 items。
   */
  private Map<String, Object> importSourceOf(Map<String, Object> task, String userId) {
    Object sourceTaskId = task.get("source_task_id");
    if (sourceTaskId == null) {
      return task;
    }
    List<Map<String, Object>> sources = jdbc.queryForList("""
        select id, user_id, bank_name, bank_description, items
          from import_task
         where id = ? and user_id = ? and source_task_id is null
        """, String.valueOf(sourceTaskId), userId);
    if (sources.isEmpty()) {
      throw new ApiException(404, "解析来源不存在");
    }
    return sources.get(0);
  }

  private void runImportInTransaction(
      String taskId,
      String userId,
      List<Map<String, Object>> chosen,
      Long targetBankId,
      String bankName,
      String description) {
    if (targetBankId == null) {
      // 只有「新建题库」才受题库数量上限约束；往已有题库里加题不占
      points.assertBankQuota(userId);
    }
    long bankId;
    if (targetBankId != null) {
      // 异步线程里再校验一次，避免任务参数被改过
      requireImportAccess(userId, targetBankId);
      bankId = targetBankId;
    } else {
      Long existing = jdbc.queryForList(
          "select id from bank where owner_id = ? and name = ?", Long.class, userId, bankName)
          .stream().findFirst().orElse(null);
      if (existing != null) {
        bankId = existing;
      } else {
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
    }

    Set<String> existingHashes = new HashSet<>(jdbc.queryForList("""
        select q.content_hash
          from question q
          join unit u on u.id = q.unit_id
         where u.bank_id = ? and q.content_hash is not null
        """, String.class, bankId));
    List<Map<String, Object>> toInsert = new ArrayList<>();
    int skippedCount = 0;
    for (Map<String, Object> item : chosen) {
      String content = String.valueOf(item.getOrDefault("content", "")).trim();
      String contentHash = QuestionContent.hash(content);
      if (content.isBlank() || !existingHashes.add(contentHash)) {
        skippedCount++;
        continue;
      }
      toInsert.add(item);
    }

    Map<String, Long> unitMap = new LinkedHashMap<>();
    for (Map<String, Object> item : toInsert) {
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

    int createdCount = 0;
    for (Map<String, Object> item : toInsert) {
      String unitName = String.valueOf(item.getOrDefault("unit", "未分类")).trim();
      Long unitId = unitMap.get(unitName.isEmpty() ? "未分类" : unitName);
      if (unitId == null) {
        continue;
      }
      String content = String.valueOf(item.getOrDefault("content", "")).trim();
      jdbc.update("""
          insert into question
            (unit_id, type, content, content_hash, options, answer, key_points,
             difficulty, explanation, source_url, status)
          values (?, ?, ?, ?, cast(? as json), ?, cast(? as json), ?, ?, ?, 'ON')
          """,
          unitId,
          String.valueOf(item.getOrDefault("type", "SINGLE")),
          content,
          QuestionContent.hash(content),
          item.get("options") == null ? null : writeJson(item.get("options")),
          String.valueOf(item.getOrDefault("answer", "")),
          item.get("key_points") == null ? null : writeJson(item.get("key_points")),
          normalizeDifficulty(item.get("difficulty")),
          item.get("explanation") == null ? null : String.valueOf(item.get("explanation")),
          item.get("source_url") == null ? null : String.valueOf(item.get("source_url")));
      createdCount++;
    }

    jdbc.update("""
        update import_task
           set bank_id = ?, progress = 100, phase = 'DONE', status = 'COMPLETED',
               created_count = ?, skipped_count = ?, error = null
         where id = ?
        """, bankId, createdCount, skippedCount, taskId);
    pruneSuccessfulImportHistory(userId);
  }

  /**
   * 每个用户只保留最近 10 条成功执行记录。执行记录删除后，
   * 若来源已无任何执行记录引用，再删除来源快照和其中的大字段。
   */
  private void pruneSuccessfulImportHistory(String userId) {
    List<Map<String, Object>> completed = jdbc.queryForList("""
        select id, source_task_id
          from import_task
         where user_id = ?
           and source_task_id is not null
           and phase = 'DONE'
           and status = 'COMPLETED'
         order by updated_at desc, created_at desc, id desc
        """, userId);
    if (completed.size() <= SUCCESSFUL_IMPORT_HISTORY_LIMIT) {
      return;
    }

    Set<String> affectedSources = new HashSet<>();
    for (Map<String, Object> job : completed.subList(SUCCESSFUL_IMPORT_HISTORY_LIMIT,
        completed.size())) {
      String jobId = String.valueOf(job.get("id"));
      Object sourceId = job.get("source_task_id");
      jdbc.update("delete from import_task where id = ? and user_id = ?", jobId, userId);
      if (sourceId != null) {
        affectedSources.add(String.valueOf(sourceId));
      }
    }
    for (String sourceId : affectedSources) {
      jdbc.update("""
          delete s
            from import_task s
            left join import_task child on child.source_task_id = s.id
           where s.id = ? and s.user_id = ? and s.source_task_id is null
             and child.id is null
          """, sourceId, userId);
    }
  }

  private void pruneAllSourceHistoriesSafely() {
    try {
      int deleted = jdbc.update("""
          delete from import_task
           where source_task_id is null
             and id in (
               select id from (
                 select id,
                        row_number() over (
                          partition by user_id
                          order by created_at desc, id desc
                        ) as history_rank
                   from import_task
                  where source_task_id is null and user_id is not null
               ) ranked
               where history_rank > ?
             )
          """, SOURCE_HISTORY_LIMIT);
      if (deleted > 0) {
        log.info("导入历史启动清理：删除 {} 条超限来源", deleted);
      }
    } catch (Exception e) {
      log.error("导入历史启动清理失败", e);
    }
  }

  private void pruneSourceHistorySafely(String userId) {
    try {
      int deleted = jdbc.update("""
          delete from import_task
           where user_id = ?
             and source_task_id is null
             and id in (
               select id from (
                 select id,
                        row_number() over (
                          order by created_at desc, id desc
                        ) as history_rank
                   from import_task
                  where user_id = ? and source_task_id is null
               ) ranked
               where history_rank > ?
             )
          """, userId, userId, SOURCE_HISTORY_LIMIT);
      if (deleted > 0) {
        log.info("导入历史清理：userId={} 删除 {} 条超限来源", userId, deleted);
      }
    } catch (Exception e) {
      log.error("导入历史清理失败：userId={}", userId, e);
    }
  }

  private List<Map<String, Object>> extractChunk(String chunk, String userId) throws Exception {
    newAccountAiLimit.check(userId);
    long start = System.currentTimeMillis();
    AiWorkflowResult result = null;
    try {
      result = ai.extractText(userId, new AiWorkflowInputs.ExtractTextInput(chunk));
      List<Map<String, Object>> items = parseQuestionItems(result.content());
      aiCallLogger.success(userId, "EXTRACT", null, result);
      return items;
    } catch (Exception e) {
      aiCallLogger.failure(
          userId,
          "EXTRACT",
          null,
          result == null ? ai.provider() : result.provider(),
          AiWorkflowKey.EXTRACT_TEXT,
          result == null ? null : result.externalRunId(),
          e.getMessage(),
          System.currentTimeMillis() - start);
      throw e;
    }
  }

  /** 截图任务：多模态一次出题，计费按 1 万字等价（10 点），失败全额退。 */
  private void runParseScreenshot(Map<String, Object> task, String taskId, String userId) {
    String dataUrl = String.valueOf(task.get("source_text"));
    jdbc.update(
        "update import_task set total_chunks = 1, done_chunks = 0 where id = ?", taskId);

    newAccountAiLimit.check(userId);
    Map<String, Object> charged = points.consumePoints(
        userId, "EXTRACT", 10_000, "import_task", taskId);
    if (!Boolean.TRUE.equals(charged.get("ok"))) {
      throw new ApiException(402, "AI 点数不足：本次需要 " + charged.get("cost")
          + " 点，当前可用 " + charged.get("available") + " 点。");
    }
    int chargedCost = intValue(charged.get("cost"));

    long start = System.currentTimeMillis();
    AiWorkflowResult result = null;
    try {
      result = ai.extractScreenshot(userId, new AiWorkflowInputs.ExtractScreenshotInput(
          dataUrl, "识别截图中的题目并输出题目 JSON"));

      List<Map<String, Object>> items = parseQuestionItems(result.content());
      if (items.isEmpty()) {
        throw new ApiException(400, "这张图里没有可用的文字内容，换一张清晰、完整的截图试试");
      }
      jdbc.update("""
          update import_task
             set items = cast(? as json), progress = 100, done_chunks = 1,
                 phase = 'READY', status = 'COMPLETED', error = null
           where id = ?
          """, writeJson(items), taskId);
      aiCallLogger.success(userId, "EXTRACT", null, result);
    } catch (Exception e) {
      aiCallLogger.failure(
          userId,
          "EXTRACT",
          null,
          result == null ? ai.provider() : result.provider(),
          AiWorkflowKey.EXTRACT_IMAGE,
          result == null ? null : result.externalRunId(),
          e.getMessage(),
          System.currentTimeMillis() - start);
      if (chargedCost > 0) {
        points.refundPoints(userId, chargedCost, "AI_SCREENSHOT_FAILED", "import_task", taskId);
      }
      if (e instanceof ApiException api) {
        throw api;
      }
      throw new ApiException(400, "截图解析失败：" + e.getMessage());
    }
  }

  /** 截图只接受 data:image/...;base64, 且解码后不超过 5MB */
  static void validateScreenshot(String dataUrl) {
    int comma = dataUrl.indexOf(',');
    if (!dataUrl.startsWith("data:image/") || comma < 0
        || !dataUrl.substring(0, comma).contains(";base64")) {
      throw new ApiException(400, "图片格式不正确，请粘贴 PNG / JPG 截图");
    }
    long approxBytes = (long) (dataUrl.length() - comma - 1) * 3 / 4;
    if (approxBytes > 5L * 1024 * 1024) {
      throw new ApiException(400, "截图太大了，请压到 5MB 以内");
    }
  }

  private List<Map<String, Object>> parseQuestionItems(String content) throws Exception {
    JsonNode root = objectMapper.readTree(content);
    JsonNode questions = root.path("questions");
    List<Map<String, Object>> items = new ArrayList<>();
    if (questions.isArray()) {
      for (JsonNode node : questions) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("unit", node.path("unit").asText("未分类"));
        item.put("type", node.path("type").asText("SINGLE"));
        item.put("content", node.path("content").asText(""));
        if (node.has("options") && node.get("options").isArray()) {
          item.put("options", objectMapper.convertValue(node.get("options"), List.class));
        }
        item.put("answer", node.path("answer").asText(""));
        if (node.has("key_points") && node.get("key_points").isArray()) {
          item.put("key_points", objectMapper.convertValue(node.get("key_points"), List.class));
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
      // 合规：只抓 robots.txt 允许的页面；不允许的直接提示改用截图识别（见 RobotsPolicy）
      return htmlToText(robotsPolicy.fetchPage(source));
    }
    byte[] bytes = Base64.getDecoder().decode(source);
    String name = String.valueOf(task.getOrDefault("source_name", "")).toLowerCase(Locale.ROOT);
    FileMagic magic = FileMagic.valueOf(bytes);
    if (name.endsWith(".pdf")) {
      try (PDDocument document = Loader.loadPDF(bytes)) {
        return new PDFTextStripper().getText(document);
      }
    }
    if (name.endsWith(".docx")
        || (name.endsWith(".doc") && magic == FileMagic.OOXML)) {
      try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
           XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
        return extractor.getText();
      }
    }
    if (name.endsWith(".doc")
        || (name.endsWith(".docx") && magic == FileMagic.OLE2)) {
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
      return objectMapper.writeValueAsString(value);
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

