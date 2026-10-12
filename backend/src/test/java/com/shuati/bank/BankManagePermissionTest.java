package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 题库维护的权限矩阵：自己的题库可以维护，公共题库只有管理员可以，别人的私有题库一律不行。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BankManagePermissionTest {

  @Autowired BankManageController controller;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ownerCanRenameAndDeleteOwnBank() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "旧名字");
    authenticate(userId, false);

    controller.updateBank(bankId, Map.of("name", "新名字", "description", "备注"));

    assertThat(bankName(bankId)).isEqualTo("新名字");
    controller.deleteBank(bankId);
    assertThat(jdbc.queryForObject("select count(*) from bank where id = ?", Integer.class, bankId))
        .isZero();
  }

  @Test
  void normalUserCannotTouchPublicBank() {
    String userId = createUser("USER");
    long publicBankId = createBank(null, "软考真题");
    String originalName = bankName(publicBankId);
    authenticate(userId, false);

    ApiException rename = assertThrows(ApiException.class, () ->
        controller.updateBank(publicBankId, Map.of("name", "被改名了")));
    assertThat(rename.getStatus()).isEqualTo(403);
    assertThat(bankName(publicBankId)).isEqualTo(originalName);

    assertThat(assertThrows(ApiException.class, () -> controller.deleteBank(publicBankId)).getStatus())
        .isEqualTo(403);
    assertThat(assertThrows(ApiException.class, () ->
        controller.createUnit(Map.of("bankId", publicBankId, "name", "插一个单元"))).getStatus())
        .isEqualTo(403);
  }

  @Test
  void normalUserCannotTouchSomeoneElsesBank() {
    String userId = createUser("USER");
    long othersBankId = createBank(createUser("USER"), "别人的题库");
    String originalName = bankName(othersBankId);
    authenticate(userId, false);

    ApiException error = assertThrows(ApiException.class, () ->
        controller.updateBank(othersBankId, Map.of("name", "被改名了")));

    assertThat(error.getStatus()).isEqualTo(403);
    assertThat(bankName(othersBankId)).isEqualTo(originalName);
  }

  @Test
  void manageListHidesOthersBanksFromNormalUsers() {
    String userId = createUser("USER");
    long ownBankId = createBank(userId, "我的题库");
    long publicBankId = createBank(null, "公共题库");
    long othersBankId = createBank(createUser("USER"), "别人的题库");
    authenticate(userId, false);

    List<Long> ids = controller.banks().data().stream()
        .map(row -> ((Number) row.get("id")).longValue())
        .toList();

    assertThat(ids).contains(ownBankId);
    assertThat(ids).doesNotContain(publicBankId, othersBankId);
  }

  @Test
  void adminSeesAndCanMaintainPublicBank() {
    String adminId = createUser("ADMIN");
    long publicBankId = createBank(null, "公共题库");
    authenticate(adminId, true);

    String renamed = "公共题库-改名-" + UUID.randomUUID();
    controller.updateBank(publicBankId, Map.of("name", renamed));

    assertThat(bankName(publicBankId)).isEqualTo(renamed);
    assertThat(controller.banks().data().stream()
        .map(row -> ((Number) row.get("id")).longValue())
        .toList()).contains(publicBankId);
  }

  @Test
  void deletingUnitOrBankWithQuestionsIsRefused() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "有题的题库");
    long unitId = createUnit(bankId, "第一章");
    createQuestion(unitId);
    authenticate(userId, false);

    ApiException unitError = assertThrows(ApiException.class, () -> controller.deleteUnit(unitId));
    assertThat(unitError.getStatus()).isEqualTo(400);
    assertThat(unitError.getMessage()).contains("请先清空题目");

    ApiException bankError = assertThrows(ApiException.class, () -> controller.deleteBank(bankId));
    assertThat(bankError.getStatus()).isEqualTo(400);
    assertThat(bankError.getMessage()).contains("请先清空题目");
  }

  @Test
  void creatingBankBeyondPlanQuotaIsRefused() {
    String userId = createUser("USER");
    createBank(userId, "已有的题库");
    authenticate(userId, false);

    // 免费版自带题库上限是 1
    ApiException error = assertThrows(ApiException.class, () ->
        controller.createBank(Map.of("name", "再建一个")));

    assertThat(error.getStatus()).isEqualTo(402);
    assertThat(error.getMessage()).contains("最多创建");
  }

  private void authenticate(String userId, boolean admin) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()));
  }

  private String createUser(String role) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', ?, 'ENABLED')
        """, id, "bankmanage-" + id.substring(0, 8) + "@example.com", role);
    return id;
  }

  private long createBank(String ownerId, String name) {
    String unique = name + "-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", unique, ownerId);
    return jdbc.queryForObject("select id from bank where name = ?", Long.class, unique);
  }

  private long createUnit(long bankId, String name) {
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 0)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private void createQuestion(long unitId) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', '题干', cast('[{"key":"A","text":"a"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId);
  }

  private String bankName(long bankId) {
    return jdbc.queryForObject("select name from bank where id = ?", String.class, bankId);
  }
}
