package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 练习光标：进度记录的是「当前刷到哪道题」，而不是「第一道没答过的题」。
 *
 * <p>用户场景：答一题 → 从头开始 → 练到第 2 题 → 退出 → 重进应停在第 2 题；
 * 哪怕第 1、2 题以前都答过。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class PracticeCursorTest {

  @Autowired BankService service;
  @Autowired BankController controller;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void cursorIsSavedAndReadBackPerScope() {
    String userId = createUser();
    long bankId = createPublicBank();
    long unitId = createUnit(bankId);
    long first = createQuestion(unitId, "第 1 题");
    long second = createQuestion(unitId, "第 2 题");
    authenticate(userId, false);

    // 没记过位置：返回 null，客户端会回退到"第一道没答过的题"（老行为不变）
    assertThat(service.cursor("BANK", bankId)).isNull();

    service.saveCursor("BANK", bankId, first);
    assertThat(service.cursor("BANK", bankId)).isEqualTo(first);

    // 同一个范围再写一次是覆盖（幂等），不会写第二行
    service.saveCursor("BANK", bankId, second);
    assertThat(service.cursor("BANK", bankId)).isEqualTo(second);
    Integer rows = jdbc.queryForObject("""
        select count(*) from practice_cursor where user_id = ? and scope_type = 'BANK' and scope_id = ?
        """, Integer.class, userId, bankId);
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void cursorsAreIsolatedBetweenScopes() {
    String userId = createUser();
    long bankId = createPublicBank();
    long unitId = createUnit(bankId);
    long unitQuestion = createQuestion(unitId, "单元里的题");
    long bankQuestion = createQuestion(unitId, "题库里的题");
    authenticate(userId, false);

    service.saveCursor("UNIT", unitId, unitQuestion);
    service.saveCursor("BANK", bankId, bankQuestion);

    assertThat(service.cursor("UNIT", unitId)).isEqualTo(unitQuestion);
    assertThat(service.cursor("BANK", bankId)).isEqualTo(bankQuestion);
    // ALL 范围用 scope_id = 0，与上面两个互不影响
    assertThat(service.cursor("ALL", 0)).isNull();
  }

  @Test
  void clearingCursorFallsBackToTheOldRule() {
    String userId = createUser();
    long bankId = createPublicBank();
    long unitId = createUnit(bankId);
    long questionId = createQuestion(unitId, "唯一一道题");
    authenticate(userId, false);

    service.saveCursor("BANK", bankId, questionId);
    service.saveCursor("BANK", bankId, null);

    assertThat(service.cursor("BANK", bankId)).isNull();
  }

  @Test
  void otherAccountsCannotSeeOrOverwriteMyCursor() {
    String owner = createUser();
    String other = createUser();
    long bankId = createPublicBank();
    long unitId = createUnit(bankId);
    long questionId = createQuestion(unitId, "题");

    authenticate(owner, false);
    service.saveCursor("BANK", bankId, questionId);

    authenticate(other, false);
    assertThat(service.cursor("BANK", bankId)).isNull();
  }

  @Test
  void cursorIsRefusedForABankYouCannotSee() {
    String owner = createUser();
    long privateBankId = createPrivateBank(owner);
    long unitId = createUnit(privateBankId);
    long questionId = createQuestion(unitId, "别人的题");

    authenticate(createUser(), false);
    ApiException read = assertThrows(
        ApiException.class, () -> service.cursor("BANK", privateBankId));
    assertThat(read.getStatus()).isEqualTo(403);
    ApiException write = assertThrows(
        ApiException.class, () -> service.saveCursor("BANK", privateBankId, questionId));
    assertThat(write.getStatus()).isEqualTo(403);
  }

  @Test
  void unknownQuestionAndBadScopeAreRefused() {
    String userId = createUser();
    long bankId = createPublicBank();
    authenticate(userId, false);

    ApiException badScope = assertThrows(
        ApiException.class, () -> service.saveCursor("WRONG", 0, null));
    assertThat(badScope.getStatus()).isEqualTo(400);

    ApiException unknownQuestion = assertThrows(
        ApiException.class, () -> service.saveCursor("BANK", bankId, 999_999_999L));
    assertThat(unknownQuestion.getStatus()).isEqualTo(404);
  }

  @Test
  void httpEndpointsReadAndWriteTheCursor() {
    String userId = createUser();
    long bankId = createPublicBank();
    long unitId = createUnit(bankId);
    long questionId = createQuestion(unitId, "题");
    authenticate(userId, false);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("scopeType", "BANK");
    body.put("scopeId", bankId);
    body.put("questionId", questionId);
    controller.saveCursor(body);

    assertThat(controller.cursor("BANK", bankId).data().get("questionId"))
        .isEqualTo(questionId);
  }

  private void authenticate(String userId, boolean admin) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "cursor-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createPublicBank() {
    return createBank(null);
  }

  private long createPrivateBank(String ownerId) {
    return createBank(ownerId);
  }

  private long createBank(String ownerId) {
    String name = "光标测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, name);
  }

  private long createUnit(long bankId) {
    String name = "单元-" + UUID.randomUUID();
    jdbc.update("insert into unit (bank_id, name, sort) values (?, ?, 1)", bankId, name);
    return jdbc.queryForObject(
        "select id from unit where bank_id = ? and name = ?", Long.class, bankId, name);
  }

  private long createQuestion(long unitId, String content) {
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', ?,
                cast('[{"key":"A","text":"a"},{"key":"B","text":"b"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId, content);
    return jdbc.queryForObject(
        "select id from question where unit_id = ? and content = ?", Long.class, unitId, content);
  }
}
