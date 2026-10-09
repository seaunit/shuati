package com.shuati.practice;

import com.shuati.ai.AiClient;
import com.shuati.ai.PromptTemplates;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import com.shuati.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiGradingService {

  private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);

  private final JdbcTemplate jdbc;
  private final AiClient ai;
  private final PointAccountService points;
  private final Json json;

  @Transactional
  public Map<String, Object> submitEssay(
      String userId, long questionId, String answer, Integer durationMs) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select type, content, answer, key_points, status from question where id = ?", questionId);
    if (rows.isEmpty() || !"SHORT".equals(rows.get(0).get("type"))
        || !"ON".equals(rows.get(0).get("status"))) {
      throw new ApiException(404, "题目不存在或不是简答题");
    }
    Map<String, Object> question = rows.get(0);

    Map<String, Object> charged = points.consumePoints(
        userId, "JUDGE", null, "question", String.valueOf(questionId));
    if (!Boolean.TRUE.equals(charged.get("ok"))) {
      throw new ApiException(402, "AI 点数不足：本次需要 " + charged.get("cost")
          + " 点，当前可用 " + charged.get("available") + " 点。可在「套餐与点数」升级套餐或购买加量包。");
    }

    String verdict = "PENDING";
    Integer score = null;
    String judgeSource = "SELF";
    Map<String, Object> feedback;
    boolean degraded = false;
    long start = System.currentTimeMillis();
    try {
      AiClient.AiResult result = ai.chat("JUDGE", PromptTemplates.ESSAY_SYSTEM,
          PromptTemplates.essayUser(
              String.valueOf(question.get("content")),
              String.valueOf(question.get("key_points")),
              String.valueOf(question.get("answer")),
              answer),
          true);
      ai.logUsage(userId, "JUDGE", result.model(), questionId, result, null,
          System.currentTimeMillis() - start);

      JsonNode parsed = ai.objectMapper().readTree(result.content());
      String rawVerdict = parsed.path("verdict").asText("").toUpperCase();
      verdict = "CORRECT".equals(rawVerdict) ? "CORRECT"
          : "PARTIAL".equals(rawVerdict) ? "PARTIAL" : "WRONG";
      score = Math.max(0, Math.min(10, parsed.path("score").asInt(0)));
      judgeSource = "AI";
      feedback = new LinkedHashMap<>();
      feedback.put("hit_points", toList(parsed.path("hit_points")));
      feedback.put("missed_points", toList(parsed.path("missed_points")));
      feedback.put("feedback", parsed.path("feedback").asText(""));
    } catch (Exception e) {
      // 判分失败不阻断提交，降级为自评；但必须留下原因，否则线上无从排查
      log.warn("AI 判分失败，已降级为自评 questionId={}", questionId, e);
      points.refundPoints(userId, intValue(charged.get("cost")), "AI_JUDGE_FAILED",
          "question", String.valueOf(questionId));
      ai.logUsage(userId, "JUDGE", "unknown", questionId, null, e.getMessage(),
          System.currentTimeMillis() - start);
      degraded = true;
      feedback = Map.of("feedback", "AI 判分暂不可用，请对照参考答案自行评估。");
    }

    KeyHolder keyHolder = new GeneratedKeyHolder();
    Map<String, Object> finalFeedback = feedback;
    String finalVerdict = verdict;
    String finalJudgeSource = judgeSource;
    Integer finalScore = score;
    jdbc.update(connection -> {
      var ps = connection.prepareStatement("""
          insert into practice_record
            (user_id, question_id, user_answer, judge_source, verdict, score, ai_feedback, duration_ms)
          values (?, ?, ?, ?, ?, ?, cast(? as json), ?)
          """, new String[]{"id"});
      ps.setString(1, userId);
      ps.setLong(2, questionId);
      ps.setString(3, answer);
      ps.setString(4, finalJudgeSource);
      ps.setString(5, finalVerdict);
      if (finalScore == null) {
        ps.setNull(6, java.sql.Types.SMALLINT);
      } else {
        ps.setInt(6, finalScore);
      }
      ps.setString(7, writeJson(finalFeedback));
      if (durationMs == null) {
        ps.setNull(8, java.sql.Types.INTEGER);
      } else {
        ps.setInt(8, durationMs);
      }
      return ps;
    }, keyHolder);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("recordId", keyHolder.getKey() == null ? null : keyHolder.getKey().longValue());
    response.put("verdict", verdict);
    response.put("score", score);
    response.put("feedback", feedback);
    response.put("referenceAnswer", question.get("answer"));
    response.put("degraded", degraded);
    return response;
  }

  @Transactional
  public Map<String, Object> explanation(String userId, long questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select content, options, answer, explanation from question where id = ?", questionId);
    if (rows.isEmpty()) {
      throw new ApiException(404, "题目不存在");
    }
    Map<String, Object> question = rows.get(0);

    Map<String, Object> charged = points.consumePoints(
        userId, "EXPLAIN", null, "question", String.valueOf(questionId));
    if (!Boolean.TRUE.equals(charged.get("ok"))) {
      throw new ApiException(402, "AI 点数不足：本次需要 " + charged.get("cost")
          + " 点，当前可用 " + charged.get("available") + " 点。可在「套餐与点数」升级套餐或购买加量包。");
    }

    Object existing = question.get("explanation");
    if (existing != null && !String.valueOf(existing).isBlank()) {
      // 缓存命中不调用 AI，但“查看 AI 解析”仍按一次解析计费并写入流水。
      return Map.of("explanation", existing);
    }

    long start = System.currentTimeMillis();
    try {
      AiClient.AiResult result = ai.chat("EXPLAIN", PromptTemplates.EXPLAIN_SYSTEM,
          PromptTemplates.explainUser(
              String.valueOf(question.get("content")),
              String.valueOf(question.get("options")),
              String.valueOf(question.get("answer"))),
          false);
      ai.logUsage(userId, "EXPLAIN", result.model(), questionId, result, null,
          System.currentTimeMillis() - start);
      jdbc.update("""
          update question set explanation = ?
           where id = ? and (explanation is null or explanation = '')
          """, result.content(), questionId);
      return Map.of("explanation", result.content());
    } catch (Exception e) {
      // 注意：本方法带 @Transactional，抛异常会把 ai_call_log 一起回滚，
      // 所以这里必须写应用日志（journalctl 可见），否则线上看不到失败原因。
      log.warn("AI 解析失败 questionId={}", questionId, e);
      points.refundPoints(userId, intValue(charged.get("cost")), "AI_EXPLAIN_FAILED",
          "question", String.valueOf(questionId));
      ai.logUsage(userId, "EXPLAIN", "unknown", questionId, null, e.getMessage(),
          System.currentTimeMillis() - start);
      String reason = e.getMessage() == null || e.getMessage().isBlank()
          ? "未知错误" : e.getMessage();
      throw new ApiException(502, "AI 解析失败：" + reason);
    }
  }

  private List<String> toList(JsonNode node) {
    List<String> values = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(item -> values.add(item.asText()));
    }
    return values;
  }

  private String writeJson(Object value) {
    try {
      return ai.objectMapper().writeValueAsString(value);
    } catch (Exception e) {
      return "{}";
    }
  }

  private int intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }
}
