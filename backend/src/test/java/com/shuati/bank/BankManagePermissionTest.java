package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import com.shuati.email.EmailPurpose;
import com.shuati.email.EmailVerificationProperties;
import com.shuati.email.EmailVerificationService;
import com.shuati.email.EmailVerificationStore;
import com.shuati.email.EmailVerificationTestSupport;
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
import org.springframework.mock.web.MockHttpServletRequest;
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
  @Autowired EmailVerificationStore emailStore;
  @Autowired EmailVerificationService emailService;
  @Autowired EmailVerificationProperties emailProperties;

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
    deleteBankWithCode(bankId, userId);
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

    assertThat(assertThrows(ApiException.class,
        () -> controller.deleteBank(publicBankId, Map.of("code", "123456"))).getStatus())
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
  void deletingUnitWithQuestionsIsRefused() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "有题的题库");
    long unitId = createUnit(bankId, "第一章");
    createQuestion(unitId);
    authenticate(userId, false);

    ApiException unitError = assertThrows(ApiException.class, () -> controller.deleteUnit(unitId));
    assertThat(unitError.getStatus()).isEqualTo(400);
    assertThat(unitError.getMessage()).contains("请先清空题目");
  }

  /** 删题库是一次性清空：单元、题目、以及这些题目的练习记录都跟着删。 */
  @Test
  void deletingBankAlsoRemovesItsUnitsQuestionsAndRecords() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "要删掉的题库");
    long unitId = createUnit(bankId, "第一章");
    long questionId = createQuestion(unitId);
    long otherQuestionId = createQuestion(createUnit(createBank(createUser("USER"), "别人的题库"), "第一章"));
    jdbc.update("""
        insert into practice_record (user_id, question_id, user_answer, judge_source, verdict)
        values (?, ?, 'A', 'LOCAL', 'CORRECT')
        """, userId, questionId);
    jdbc.update("""
        insert into practice_session (user_id, scope_type, scope_id, scope_name)
        values (?, 'BANK', ?, '要删掉的题库')
        """, userId, bankId);
    authenticate(userId, false);

    deleteBankWithCode(bankId, userId);

    assertThat(jdbc.queryForObject("select count(*) from bank where id = ?", Integer.class, bankId))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from unit where bank_id = ?", Integer.class, bankId))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from question where id = ?", Integer.class, questionId))
        .isZero();
    assertThat(jdbc.queryForObject(
        "select count(*) from practice_record where question_id = ?", Integer.class, questionId))
        .isZero();
    assertThat(jdbc.queryForObject("""
        select count(*) from practice_session where scope_type = 'BANK' and scope_id = ?
        """, Integer.class, bankId)).isZero();
    // 别人的题库不受牵连
    assertThat(jdbc.queryForObject(
        "select count(*) from question where id = ?", Integer.class, otherQuestionId)).isEqualTo(1);
  }

  @Test
  void deletingBankRequiresTheEmailCode() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    authenticate(userId, false);

    // 没带验证码
    assertThat(assertThrows(ApiException.class, () -> controller.deleteBank(bankId, Map.of()))
        .getStatus()).isEqualTo(400);
    // 码填错了
    seedCode(userId, "123456", EmailPurpose.DELETE_BANK);
    assertThat(assertThrows(ApiException.class,
        () -> controller.deleteBank(bankId, Map.of("code", "000000"))).getStatus()).isEqualTo(400);
    assertThat(bankName(bankId)).isNotBlank();
  }

  /** 注册/重置密码的验证码不能拿来删题库，用途要分开。 */
  @Test
  void deleteCodeFromAnotherPurposeIsRejected() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    seedCode(userId, "123456", EmailPurpose.RESET);
    authenticate(userId, false);

    assertThat(assertThrows(ApiException.class,
        () -> controller.deleteBank(bankId, Map.of("code", "123456"))).getStatus()).isEqualTo(400);
    assertThat(bankName(bankId)).isNotBlank();
  }

  @Test
  void deleteCodeIsSentToTheAccountEmail() {
    String userId = createUser("USER");
    long bankId = createBank(userId, "我的题库");
    authenticate(userId, false);

    EmailVerificationService.SendResult result =
        controller.sendDeleteBankCode(bankId, new MockHttpServletRequest()).data();

    assertThat(result.cooldownSeconds()).isPositive();
    assertThat(result.expiresInSeconds()).isPositive();
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

  /** 走完「发码 → 校验 → 删除」，测试里直接把码种进 Redis，不发真邮件 */
  private void deleteBankWithCode(long bankId, String userId) {
    seedCode(userId, "123456", EmailPurpose.DELETE_BANK);
    controller.deleteBank(bankId, Map.of("code", "123456"));
  }

  private void seedCode(String userId, String code, EmailPurpose purpose) {
    EmailVerificationTestSupport.seed(
        emailStore, emailService, emailProperties, emailOf(userId), code, purpose);
  }

  private String emailOf(String userId) {
    return jdbc.queryForObject("select email from profiles where id = ?", String.class, userId);
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

  private long createQuestion(long unitId) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', '题干', cast('[{"key":"A","text":"a"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? order by id desc limit 1", Long.class, unitId);
  }

  private String bankName(long bankId) {
    return jdbc.queryForObject("select name from bank where id = ?", String.class, bankId);
  }
}
