package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「导入到已有题库」的权限控制：
 * 自己的题库可以；公共题库只有管理员可以；别人的私有题库一律不行。
 * 不允许时必须在提交阶段就拒绝，不能等到异步执行才发现。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ImportTargetBankTest {

  @Autowired ImportService importService;
  @Autowired JdbcTemplate jdbc;

  @Test
  void regularUserCanImportIntoOwnBank() {
    String userId = createUser("USER");
    long bankId = createBank(userId);
    String taskId = createReadyTask(userId, "我的题库");

    importService.requestImport(userId, taskId, List.of(0), bankId);

    assertThat(targetBankOf(taskId)).isEqualTo(bankId);
  }

  @Test
  void regularUserCannotImportIntoPublicBank() {
    String userId = createUser("USER");
    long publicBankId = createBank(null);
    String taskId = createReadyTask(userId, "公共题库");

    ApiException error = assertThrows(ApiException.class, () ->
        importService.requestImport(userId, taskId, List.of(0), publicBankId));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(error.getMessage()).contains("公共题库只能由管理员维护");
    assertThat(targetBankOf(taskId)).isNull();
  }

  @Test
  void regularUserCannotImportIntoSomeoneElsesBank() {
    String userId = createUser("USER");
    long othersBankId = createBank(createUser("USER"));
    String taskId = createReadyTask(userId, "别人的题库");

    ApiException error = assertThrows(ApiException.class, () ->
        importService.requestImport(userId, taskId, List.of(0), othersBankId));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(targetBankOf(taskId)).isNull();
  }

  @Test
  void adminCanImportIntoPublicBank() {
    String adminId = createUser("ADMIN");
    long publicBankId = createBank(null);
    String taskId = createReadyTask(adminId, "公共题库");

    importService.requestImport(adminId, taskId, List.of(0), publicBankId);

    assertThat(targetBankOf(taskId)).isEqualTo(publicBankId);
  }

  @Test
  void creatingNewBankRequiresAName() {
    String userId = createUser("USER");
    String taskId = createReadyTask(userId, "");

    ApiException error = assertThrows(ApiException.class, () ->
        importService.requestImport(userId, taskId, List.of(0), null));

    assertThat(error.getStatus()).isEqualTo(400);
    assertThat(error.getMessage()).contains("请先填写题库名称");
  }

  private String createUser(String role) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', ?, 'ENABLED')
        """, id, "importbank-" + id.substring(0, 8) + "@example.com", role);
    return id;
  }

  private long createBank(String ownerId) {
    String name = "导入目标-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, name);
  }

  private String createReadyTask(String userId, String bankName) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into import_task
          (id, user_id, kind, phase, status, bank_name, source_text, items)
        values (?, ?, 'text', 'READY', 'COMPLETED', ?, '源文本', cast('[{"type":"SINGLE"}]' as json))
        """, id, userId, bankName);
    return id;
  }

  private Long targetBankOf(String taskId) {
    return jdbc.queryForObject(
        "select target_bank_id from import_task where id = ?", Long.class, taskId);
  }
}
