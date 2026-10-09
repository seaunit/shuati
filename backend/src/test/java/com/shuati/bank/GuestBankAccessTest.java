package com.shuati.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 游客模式：首页的公共题库列表与单元允许匿名读取，私有题库一律拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestBankAccessTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void guestOnlySeesPublicBanks() throws Exception {
    long publicBankId = createBank(null);
    long privateBankId = createBank(createUser());

    String body = mvc.perform(get("/api/banks"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    List<Map<String, Object>> banks = objectMapper.readValue(
        objectMapper.readTree(body).path("data").toString(), new TypeReference<>() {});
    List<Long> ids = banks.stream()
        .map(bank -> ((Number) bank.get("id")).longValue())
        .toList();

    assertThat(ids).contains(publicBankId);
    assertThat(ids).doesNotContain(privateBankId);
  }

  @Test
  void guestCanReadPublicBankUnitsButNotPrivateOnes() throws Exception {
    long publicBankId = createBank(null);
    long privateBankId = createBank(createUser());

    mvc.perform(get("/api/banks/" + publicBankId + "/units"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/banks/" + privateBankId + "/units"))
        .andExpect(status().isForbidden());
  }

  private long createBank(String ownerId) {
    String name = "游客题库测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, ?)", name, ownerId);
    return jdbc.queryForObject(
        "select id from bank where name = ? order by id desc limit 1", Long.class, name);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "guest-bank-" + id.substring(0, 8) + "@example.com");
    return id;
  }
}
