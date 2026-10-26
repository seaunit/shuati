package com.shuati.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuati.auth.CustomHeaderFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiUsageAuditTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void adminCanDistinguishDifyWorkflowAndExternalRun() throws Exception {
    String adminId = createUser("ADMIN", "audit-admin@example.com");
    String userId = createUser("USER", "audit-user@example.com");
    jdbc.update("""
        insert into ai_call_log
          (user_id, purpose, provider, workflow_key, external_run_id, model,
           total_tokens, success, duration_ms)
        values (?, 'JUDGE', 'DIFY', 'judge', 'run-visible', 'dify:judge', 25, 1, 180)
        """, userId);

    mvc.perform(get("/api/admin/stats/ai-usage").with(asAdmin(adminId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.ai_calls").value(1))
        .andExpect(jsonPath("$.data.recent_calls[0].provider").value("DIFY"))
        .andExpect(jsonPath("$.data.recent_calls[0].workflow_key").value("judge"))
        .andExpect(jsonPath("$.data.recent_calls[0].external_run_id").value("run-visible"));
  }

  private String createUser(String role, String email) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', ?, 'ENABLED')
        """, id, email, role);
    return id;
  }

  private RequestPostProcessor asAdmin(String adminId) {
    return request -> {
      authentication(new UsernamePasswordAuthenticationToken(
          adminId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .postProcessRequest(request);
      request.addHeader(CustomHeaderFilter.HEADER, CustomHeaderFilter.VALUE);
      return request;
    };
  }
}
