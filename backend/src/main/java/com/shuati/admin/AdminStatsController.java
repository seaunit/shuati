package com.shuati.admin;

import com.shuati.ai.AesCrypto;
import com.shuati.ai.AiClient;
import com.shuati.billing.BillingService;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.Json;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequiredArgsConstructor
public class AdminStatsController {

  private final JdbcTemplate jdbc;
  private final Json json;
  private final AesCrypto aes;
  private final BillingService billingService;
  private final PointAccountService points;

  @GetMapping("/api/admin/stats/overview")
  public ApiResponse<Map<String, Object>> overview() {
    int userCount = count("select count(*) from profiles");
    int questionCount = count("select count(*) from question");
    int answerCount = count("select count(*) from practice_record");
    int todayActive = count("""
        select count(distinct user_id) from practice_record
         where created_at >= utc_date()
        """);
    List<Map<String, Object>> verdicts = jdbc.queryForList(
        "select verdict, count(*) as n from practice_record group by verdict");
    Map<String, Object> verdictMap = new LinkedHashMap<>();
    verdictMap.put("CORRECT", 0);
    verdictMap.put("PARTIAL", 0);
    verdictMap.put("WRONG", 0);
    verdictMap.put("PENDING", 0);
    for (Map<String, Object> row : verdicts) {
      verdictMap.put(String.valueOf(row.get("verdict")), row.get("n"));
    }
    List<Map<String, Object>> aiRows = jdbc.queryForList(
        "select success, total_tokens from ai_call_log");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userCount", userCount);
    result.put("questionCount", questionCount);
    result.put("answerCount", answerCount);
    result.put("todayActiveUsers", todayActive);
    result.put("verdicts", verdictMap);
    result.put("ai_calls", aiRows.size());
    result.put("total_tokens", aiRows.stream()
        .mapToLong(r -> intValue(r.get("total_tokens"))).sum());
    return ApiResponse.ok(result);
  }

  @GetMapping("/api/admin/stats/by-unit")
  public ApiResponse<List<Map<String, Object>>> byUnit() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select b.name as bank_name, u.name as unit_name,
               count(r.id) as total,
               sum(case when r.verdict = 'CORRECT' then 1 else 0 end) as correct
          from practice_record r
          join question q on q.id = r.question_id
          join unit u on u.id = q.unit_id
          join bank b on b.id = u.bank_id
         group by b.id, b.name, u.id, u.name
         order by b.name, u.name
        """);
    return ApiResponse.ok(rows);
  }

  @GetMapping("/api/admin/stats/hardest-questions")
  public ApiResponse<List<Map<String, Object>>> hardestQuestions() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select q.id, q.content, b.name as bank_name, u.name as unit_name,
               count(r.id) as attempts,
               sum(case when r.verdict = 'CORRECT' then 1 else 0 end) as correct
          from practice_record r
          join question q on q.id = r.question_id
          join unit u on u.id = q.unit_id
          join bank b on b.id = u.bank_id
         group by q.id, q.content, b.name, u.name
         having attempts > 0
         order by (sum(case when r.verdict = 'CORRECT' then 1 else 0 end) / count(r.id)) asc,
                  attempts desc
         limit 20
        """);
    return ApiResponse.ok(rows);
  }

  @GetMapping("/api/admin/stats/ai-usage")
  public ApiResponse<Map<String, Object>> aiUsage() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select purpose, success, prompt_tokens, completion_tokens, total_tokens, duration_ms, created_at
          from ai_call_log
        """);
    long failed = rows.stream().filter(r -> intValue(r.get("success")) == 0).count();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("ai_calls", rows.size());
    summary.put("failed_calls", failed);
    summary.put("prompt_tokens", rows.stream().mapToLong(r -> intValue(r.get("prompt_tokens"))).sum());
    summary.put("completion_tokens", rows.stream()
        .mapToLong(r -> intValue(r.get("completion_tokens"))).sum());
    summary.put("total_tokens", rows.stream().mapToLong(r -> intValue(r.get("total_tokens"))).sum());
    long totalDuration = rows.stream().mapToLong(r -> intValue(r.get("duration_ms"))).sum();
    summary.put("avg_duration_ms", rows.isEmpty() ? 0 : Math.round((double) totalDuration / rows.size()));

    Map<String, Map<String, Object>> byPurpose = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String purpose = String.valueOf(row.get("purpose"));
      Map<String, Object> group = byPurpose.computeIfAbsent(purpose, key -> {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("calls", 0);
        created.put("total_tokens", 0);
        return created;
      });
      group.put("calls", ((Number) group.get("calls")).intValue() + 1);
      group.put("total_tokens", ((Number) group.get("total_tokens")).intValue()
          + intValue(row.get("total_tokens")));
    }
    List<Map<String, Object>> purposeList = new ArrayList<>();
    byPurpose.forEach((purpose, value) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("purpose", purpose);
      item.putAll(value);
      purposeList.add(item);
    });

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("summary", summary);
    result.put("by_purpose", purposeList);
    return ApiResponse.ok(result);
  }

  @GetMapping("/api/admin/billing")
  public ApiResponse<Map<String, Object>> billing() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("plans", json.normalize(jdbc.queryForList("select * from plan order by sort"), Set.of("features")));
    result.put("packs", jdbc.queryForList("select * from point_pack order by sort"));
    result.put("rules", jdbc.queryForList("select * from ai_price_rule order by purpose"));
    result.put("configs", jdbc.queryForList("select * from billing_config order by `key`"));
    result.put("orders", billingService.allOrders(100));
    result.put("stats", billingService.adminSummary());
    return ApiResponse.ok(result);
  }

  @PatchMapping("/api/admin/billing")
  public ApiResponse<Void> updateBilling(@RequestBody Map<String, Object> body) {
    String type = String.valueOf(body.getOrDefault("type", "")).toUpperCase();
    switch (type) {
      case "PLAN" -> {
        String code = String.valueOf(body.getOrDefault("code", ""));
        if (code.isEmpty()) {
          throw new ApiException(400, "缺少套餐编码");
        }
        jdbc.update("""
            update plan set name = coalesce(?, name), tagline = ?, description = ?,
                price_monthly_cents = coalesce(?, price_monthly_cents),
                price_quarterly_cents = coalesce(?, price_quarterly_cents),
                price_yearly_cents = coalesce(?, price_yearly_cents),
                monthly_points = coalesce(?, monthly_points),
                max_banks = ?, max_questions = ?, retention_days = ?,
                allow_pro_model = coalesce(?, allow_pro_model),
                show_on_pricing = coalesce(?, show_on_pricing),
                status = coalesce(?, status)
              where code = ?
            """,
            body.get("name"), body.get("tagline"), body.get("description"),
            body.get("price_monthly_cents"), body.get("price_quarterly_cents"),
            body.get("price_yearly_cents"), body.get("monthly_points"),
            body.get("max_banks"), body.get("max_questions"), body.get("retention_days"),
            boolValue(body.get("allow_pro_model")), boolValue(body.get("show_on_pricing")),
            body.get("status"), code);
        if (body.get("features") instanceof List<?> features) {
          jdbc.update("update plan set features = cast(? as json) where code = ?",
              writeJson(features), code);
        }
      }
      case "PACK" -> {
        String code = String.valueOf(body.getOrDefault("code", ""));
        jdbc.update("""
            update point_pack set name = coalesce(?, name), price_cents = coalesce(?, price_cents),
                points = coalesce(?, points), bonus_points = coalesce(?, bonus_points),
                sort = coalesce(?, sort), status = coalesce(?, status)
              where code = ?
            """, body.get("name"), body.get("price_cents"), body.get("points"),
            body.get("bonus_points"), body.get("sort"), body.get("status"), code);
      }
      case "RULE" -> {
        String purpose = String.valueOf(body.getOrDefault("purpose", "")).toUpperCase();
        jdbc.update("update ai_price_rule set points = ? where purpose = ?",
            intValue(body.get("points")), purpose);
      }
      case "CONFIG" -> {
        String key = String.valueOf(body.getOrDefault("key", ""));
        jdbc.update("update billing_config set value = cast(? as json) where `key` = ?",
            writeJson(body.get("value")), key);
      }
      default -> throw new ApiException(400, "不支持的更新类型");
    }
    return ApiResponse.ok(null);
  }

  @PostMapping("/api/admin/orders/{id}/settle")
  public ApiResponse<Void> settleOrder(@PathVariable String id, @RequestBody Map<String, Object> body) {
    billingService.settleOrder(id, String.valueOf(body.getOrDefault("action", "PAID")));
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/admin/ai-config")
  public ApiResponse<List<Map<String, Object>>> aiConfigs() {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select id, name, base_url, protocol, model, purpose, status, remark,
               api_key_encrypted, created_at, updated_at
          from ai_config order by id asc
        """);
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> view = new LinkedHashMap<>(row);
      String encrypted = String.valueOf(row.remove("api_key_encrypted"));
      try {
        view.put("api_key_masked", aes.mask(aes.decrypt(encrypted)));
      } catch (Exception e) {
        view.put("api_key_masked", "****");
      }
      view.remove("api_key_encrypted");
      result.add(view);
    }
    return ApiResponse.ok(result);
  }

  /** 单条 AI 配置维护：不存在则创建，存在则覆盖，保存后强制启用。 */
  @PutMapping("/api/admin/ai-config")
  public ApiResponse<Void> saveAiConfig(@RequestBody Map<String, Object> body) {
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    String baseUrl = body.get("baseUrl") == null ? "" : String.valueOf(body.get("baseUrl")).trim();
    String protocol = body.get("protocol") == null
        ? "" : String.valueOf(body.get("protocol")).trim().toUpperCase();
    String apiKey = body.get("apiKey") == null ? "" : String.valueOf(body.get("apiKey")).trim();
    String model = String.valueOf(body.getOrDefault("model", "")).trim();
    String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));

    // 单条 AI 配置：所有项均为必填
    if (name.isEmpty()) {
      throw new ApiException(400, "配置名称不能为空");
    }
    if (protocol.isEmpty()) {
      throw new ApiException(400, "请选择协议");
    }
    if (!List.of(AiClient.PROTOCOL_OPENAI, AiClient.PROTOCOL_ANTHROPIC).contains(protocol)) {
      throw new ApiException(400, "协议只能是 OPENAI / ANTHROPIC");
    }
    if (baseUrl.isEmpty()) {
      throw new ApiException(400, "Base URL 不能为空");
    }
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw new ApiException(400, "Base URL 必须以 http:// 或 https:// 开头");
    }
    if (apiKey.isEmpty()) {
      throw new ApiException(400, "API Key 不能为空");
    }
    if (model.isEmpty()) {
      throw new ApiException(400, "模型不能为空");
    }
    baseUrl = baseUrl.replaceAll("/+$", "");

    List<Map<String, Object>> rows = jdbc.queryForList(
        "select id from ai_config order by id asc limit 1");
    if (rows.isEmpty()) {
      jdbc.update("""
          insert into ai_config
            (name, base_url, protocol, api_key_encrypted, model, purpose, status, remark)
          values (?, ?, ?, ?, ?, 'BOTH', 'ENABLED', ?)
          """, name, baseUrl, protocol, aes.encrypt(apiKey), model, remark);
      return ApiResponse.ok(null);
    }

    long id = ((Number) rows.get(0).get("id")).longValue();
    jdbc.update("""
        update ai_config
           set name = ?, base_url = ?, protocol = ?, api_key_encrypted = ?,
               model = ?, status = 'ENABLED', remark = ?
         where id = ?
        """, name, baseUrl, protocol, aes.encrypt(apiKey), model, remark, id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/api/admin/ai-config")
  public ApiResponse<Void> createAiConfig(@RequestBody Map<String, Object> body) {
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    String baseUrl = body.get("baseUrl") == null ? "" : String.valueOf(body.get("baseUrl")).trim();
    String protocol = AiClient.normalizeProtocol(
        body.get("protocol") == null ? null : String.valueOf(body.get("protocol")));
    String apiKey = String.valueOf(body.getOrDefault("apiKey", "")).trim();
    String model = String.valueOf(body.getOrDefault("model", "")).trim();
    if (name.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
      throw new ApiException(400, "名称、API Key、模型均必填");
    }
    baseUrl = baseUrl.replaceAll("/+$", "");
    if (baseUrl.isEmpty()) {
      baseUrl = AiClient.defaultBaseUrl(protocol);
    }
    jdbc.update("""
        insert into ai_config (name, base_url, protocol, api_key_encrypted, model, purpose, remark)
        values (?, ?, ?, ?, ?, ?, ?)
        """, name, baseUrl, protocol, aes.encrypt(apiKey), model,
        String.valueOf(body.getOrDefault("purpose", "BOTH")),
        body.get("remark") == null ? null : String.valueOf(body.get("remark")));
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/admin/ai-config/{id}")
  public ApiResponse<Void> updateAiConfig(@PathVariable long id, @RequestBody Map<String, Object> body) {
    if (body.get("apiKey") != null && !String.valueOf(body.get("apiKey")).isBlank()) {
      jdbc.update("update ai_config set api_key_encrypted = ? where id = ?",
          aes.encrypt(String.valueOf(body.get("apiKey"))), id);
    }
    jdbc.update("""
        update ai_config
           set name = coalesce(?, name), base_url = coalesce(?, base_url),
               protocol = coalesce(?, protocol), model = coalesce(?, model),
               purpose = coalesce(?, purpose), remark = ?
         where id = ?
        """, body.get("name"), body.get("baseUrl"), body.get("protocol"), body.get("model"),
        body.get("purpose"),
        body.get("remark") == null ? null : String.valueOf(body.get("remark")), id);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/api/admin/ai-config/{id}")
  public ApiResponse<Void> deleteAiConfig(@PathVariable long id) {
    jdbc.update("delete from ai_config where id = ?", id);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/api/admin/ai-config/{id}/status")
  public ApiResponse<Void> aiConfigStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
    String status = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
    if (!List.of("ENABLED", "DISABLED").contains(status)) {
      throw new ApiException(400, "状态不正确");
    }
    jdbc.update("update ai_config set status = ? where id = ?", status, id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/api/admin/ai-config/test")
  public ApiResponse<Map<String, Object>> testAiConfig(@RequestBody Map<String, Object> body) {
    String baseUrl = String.valueOf(body.getOrDefault("baseUrl", "")).trim();
    String apiKey = String.valueOf(body.getOrDefault("apiKey", "")).trim();
    String model = String.valueOf(body.getOrDefault("model", "")).trim();
    String protocol = body.get("protocol") == null ? null : String.valueOf(body.get("protocol"));

    // 未显式传入时，回退到库里已保存的配置（前端默认走这条路径）
    if (body.get("id") != null) {
      long id;
      try {
        id = (long) Double.parseDouble(String.valueOf(body.get("id")));
      } catch (NumberFormatException e) {
        id = 0;
      }
      if (id > 0) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select base_url, protocol, api_key_encrypted, model from ai_config where id = ?", id);
        if (!rows.isEmpty()) {
          Map<String, Object> saved = rows.get(0);
          if (baseUrl.isEmpty()) {
            baseUrl = String.valueOf(saved.get("base_url"));
          }
          if (protocol == null || protocol.isBlank()) {
            protocol = String.valueOf(saved.get("protocol"));
          }
          if (model.isEmpty()) {
            model = String.valueOf(saved.get("model"));
          }
          if (apiKey.isEmpty()) {
            apiKey = aes.decrypt(String.valueOf(saved.get("api_key_encrypted")));
          }
        }
      }
    }
    protocol = AiClient.normalizeProtocol(protocol);
    if (baseUrl.isEmpty()) {
      baseUrl = AiClient.defaultBaseUrl(protocol);
    }
    if (model.isEmpty()) {
      model = "deepseek-flash";
    }
    baseUrl = baseUrl.replaceAll("/+$", "");
    if (apiKey.isEmpty()) {
      throw new ApiException(400, "API Key 必填");
    }
    boolean anthropic = AiClient.isAnthropic(protocol);
    long start = System.currentTimeMillis();
    try {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(15));
      factory.setReadTimeout(Duration.ofSeconds(60));
      RestClient client = RestClient.builder().requestFactory(factory).build();
      Map<?, ?> response = anthropic
          ? client.post().uri(baseUrl + "/v1/messages")
              .header("x-api-key", apiKey)
              .header("anthropic-version", "2023-06-01")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of(
                  "model", model,
                  "max_tokens", 64,
                  "messages", List.of(
                      Map.of("role", "user", "content", "回复 ok 两个字即可。"))))
              .retrieve().body(Map.class)
          : client.post().uri(baseUrl + "/chat/completions")
              .header("Authorization", "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of(
                  "model", model,
                  "messages", List.of(
                      Map.of("role", "system", "content", "你是连通性测试助手。"),
                      Map.of("role", "user", "content", "回复 ok 两个字即可。")),
                  "temperature", 0))
              .retrieve().body(Map.class);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", true);
      result.put("latencyMs", System.currentTimeMillis() - start);
      result.put("reply", response == null ? "" : String.valueOf(response).substring(
          0, Math.min(200, String.valueOf(response).length())));
      return ApiResponse.ok(result);
    } catch (Exception e) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", false);
      result.put("latencyMs", System.currentTimeMillis() - start);
      result.put("error", e.getMessage() == null ? "请求失败"
          : e.getMessage().substring(0, Math.min(200, e.getMessage().length())));
      return ApiResponse.ok(result);
    }
  }

  private int count(String sql) {
    Integer value = jdbc.queryForObject(sql, Integer.class);
    return value == null ? 0 : value;
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

  private Integer boolValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean bool) {
      return bool ? 1 : 0;
    }
    return "true".equalsIgnoreCase(String.valueOf(value))
        || "1".equals(String.valueOf(value)) ? 1 : 0;
  }

  private String writeJson(Object value) {
    try {
      return json.objectMapper().writeValueAsString(value);
    } catch (Exception e) {
      return "null";
    }
  }
}

