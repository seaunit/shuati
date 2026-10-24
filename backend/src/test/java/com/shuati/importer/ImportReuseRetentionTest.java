package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ImportReuseRetentionTest {

  @Autowired ImportService importService;
  @Autowired JdbcTemplate jdbc;

  @Test
  void sourceCanBeImportedAgainWithoutBeingOverwritten() {
    String userId = createUser("USER");
    long firstBank = createBank(userId);
    long secondBank = createBank(userId);
    String sourceId = createSource(userId, "复用来源", items(
        question("Java 基础", "第一题"),
        question("Java 基础", "第二题")));

    String firstJob = startImport(userId, sourceId, List.of(0, 1), firstBank);
    String secondJob = startImport(userId, sourceId, List.of(0, 1), secondBank);

    assertThat(firstJob).isNotEqualTo(secondJob);
    assertThat(questionCount(firstBank)).isEqualTo(2);
    assertThat(questionCount(secondBank)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "select phase from import_task where id = ?", String.class, sourceId))
        .isEqualTo("READY");
    assertThat(jdbc.queryForObject(
        "select count(*) from import_task where source_task_id = ?", Integer.class, sourceId))
        .isEqualTo(2);
  }

  @Test
  void duplicateContentIsSkippedWithinOneBankButAllowedInAnotherBank() {
    String userId = createUser("USER");
    long firstBank = createBank(userId);
    long secondBank = createBank(userId);
    String sourceId = createSource(userId, "去重来源", items(
        question("Java 基础", "题目 A"),
        question("Java 基础", " 题目   A "),
        question("Java 基础", "题目 B")));

    String firstJob = startImport(userId, sourceId, List.of(0, 1, 2), firstBank);
    String secondJob = startImport(userId, sourceId, List.of(0, 1, 2), firstBank);
    String otherBankJob = startImport(userId, sourceId, List.of(0, 1, 2), secondBank);

    assertThat(count(firstJob, "created_count")).isEqualTo(2);
    assertThat(count(firstJob, "skipped_count")).isEqualTo(1);
    assertThat(count(secondJob, "created_count")).isZero();
    assertThat(count(secondJob, "skipped_count")).isEqualTo(3);
    assertThat(questionCount(firstBank)).isEqualTo(2);
    assertThat(count(otherBankJob, "created_count")).isEqualTo(2);
    assertThat(questionCount(secondBank)).isEqualTo(2);
  }

  @Test
  void keepsLatestTenSuccessfulImportsPerUserAndPreservesQuestions() {
    String userId = createUser("USER");
    String otherUserId = createUser("USER");
    long bankId = createBank(userId);
    long otherBankId = createBank(otherUserId);
    String oldestSourceId = null;
    for (int i = 1; i <= 10; i++) {
      String sourceId = createSource(userId, "历史来源 " + i, items(
          question("历史", "历史题目 " + i)));
      if (i == 1) {
        oldestSourceId = sourceId;
      }
      seedCompletedJob(userId, sourceId, bankId,
          "2026-10-01 00:00:" + String.format("%02d", i));
    }
    String otherSourceId = createSource(otherUserId, "他人来源", items(
        question("他人", "他人题目")));
    seedCompletedJob(otherUserId, otherSourceId, otherBankId, "2026-10-01 00:00:01");
    jdbc.update("""
        insert into unit (bank_id, name, sort) values (?, '保留题目', 0)
        """, bankId);
    long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = '保留题目'", Long.class, bankId);
    jdbc.update("""
        insert into question (unit_id, type, content, content_hash, options, answer)
        values (?, 'SINGLE', '已导入题目仍需保留', 'existing-hash',
                cast('[{"key":"A","text":"正确"}]' as json), 'A')
        """, unitId);

    String liveSource = createSource(userId, "最新来源", items(
        question("最新", "最新题目")));
    startImport(userId, liveSource, List.of(0), bankId);

    assertThat(jdbc.queryForObject("""
        select count(*) from import_task
         where user_id = ? and source_task_id is not null
           and phase = 'DONE' and status = 'COMPLETED'
        """, Integer.class, userId)).isEqualTo(10);
    assertThat(jdbc.queryForObject(
        "select count(*) from import_task where id = ?", Integer.class, oldestSourceId)).isZero();
    assertThat(jdbc.queryForObject(
        "select count(*) from import_task where source_task_id = ?", Integer.class, otherSourceId))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "select count(*) from question where unit_id = ?", Integer.class, unitId)).isEqualTo(1);
    assertThat(questionCount(bankId)).isEqualTo(2);
  }

  @Test
  void userCannotReuseAnotherUsersSourceEvenWhenAdmin() {
    String ownerId = createUser("USER");
    String adminId = createUser("ADMIN");
    String sourceId = createSource(ownerId, "私有来源", items(
        question("权限", "私有题目")));
    long adminBankId = createBank(adminId);

    ApiException error = assertThrows(ApiException.class, () ->
        importService.requestImport(adminId, sourceId, List.of(0), adminBankId));

    assertThat(error.getStatus()).isEqualTo(404);
    assertThat(jdbc.queryForObject(
        "select count(*) from import_task where source_task_id = ?", Integer.class, sourceId))
        .isZero();
  }

  private String startImport(
      String userId, String sourceId, List<Integer> indexes, long targetBankId) {
    importService.requestImport(userId, sourceId, indexes, targetBankId);
    List<String> jobIds = jdbc.queryForList("""
        select id from import_task
         where source_task_id = ?
         order by created_at desc, id desc
         limit 1
        """, String.class, sourceId);
    assertThat(jobIds).as("requestImport 应创建独立执行记录").hasSize(1);
    importService.runTask(jobIds.get(0));
    return jobIds.get(0);
  }

  private void seedCompletedJob(
      String userId, String sourceId, long bankId, String completedAt) {
    jdbc.update("""
        insert into import_task
          (id, user_id, source_task_id, kind, phase, status, progress,
           total_chunks, done_chunks, bank_name, selected_indexes, bank_id,
           target_bank_id, created_count, skipped_count, created_at, updated_at)
        values (?, ?, ?, 'text', 'DONE', 'COMPLETED', 100,
                1, 1, '历史任务', cast('[]' as json), ?, ?, 1, 0, ?, ?)
        """, UUID.randomUUID().toString(), userId, sourceId, bankId, bankId,
        completedAt, completedAt);
  }

  private String createUser(String role) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', ?, 'ENABLED')
        """, id, "import-reuse-" + id.substring(0, 8) + "@example.com", role);
    return id;
  }

  private long createBank(String ownerId) {
    String name = "复用测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, name);
  }

  private String createSource(String userId, String bankName, String itemsJson) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into import_task
          (id, user_id, kind, phase, status, progress, total_chunks, done_chunks,
           bank_name, source_text, items)
        values (?, ?, 'text', 'READY', 'COMPLETED', 100, 1, 1,
                ?, '来源文本', cast(? as json))
        """, id, userId, bankName, itemsJson);
    return id;
  }

  private String items(String... questions) {
    return "[" + String.join(",", questions) + "]";
  }

  private String question(String unit, String content) {
    return """
        {"unit":"%s","type":"SINGLE","content":"%s",
         "options":[{"key":"A","text":"正确选项"}],"answer":"A","difficulty":"EASY"}
        """.formatted(unit, content);
  }

  private int questionCount(long bankId) {
    return jdbc.queryForObject("""
        select count(*) from question q join unit u on u.id = q.unit_id
         where u.bank_id = ?
        """, Integer.class, bankId);
  }

  private int count(String jobId, String column) {
    if (!List.of("created_count", "skipped_count").contains(column)) {
      throw new IllegalArgumentException("unsupported column");
    }
    return jdbc.queryForObject(
        "select " + column + " from import_task where id = ?", Integer.class, jobId);
  }
}
