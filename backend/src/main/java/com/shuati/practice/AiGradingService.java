package com.shuati.practice;

import com.shuati.abuse.NewAccountAiLimit;
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
  private final ExplanationSaveQueue explanationQueue;
  private final NewAccountAiLimit newAccountAiLimit;

  @Transactional
  public Map<String, Object> submitEssay(
      String userId, long questionId, String answer, Integer durationMs) {
    // 新号首日额度：在扣点之前拦，避免"扣了点又被额度拒掉"
    newAccountAiLimit.check(userId);
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
              // 为空时传 null，由提示词决定「自己提炼评分要点」，别把 "null" 字符串塞给模型
              question.get("key_points") == null
                  ? null
                  : String.valueOf(question.get("key_points")),
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

  /**
   * 注意：这里刻意<b>不加</b> {@code @Transactional}。AI 调用是秒级的，如果整个方法在一个事务里，
   * 就会在 AI 调用期间一直占着数据库连接，并且锁住该用户的点数账户行
   * （{@link PointAccountService#consumePoints} 里有 {@code SELECT ... FOR UPDATE}），
   * 几个人同时点解析就可能把连接池占满。所以扣点各自成短事务，落库交给 MQ。
   */
  public Map<String, Object> explanation(String userId, long questionId) {
    newAccountAiLimit.check(userId);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select content, options, answer from question where id = ?", questionId);
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

    // 解析库（question.explanation）只用于刷题时直接带出，不在这里复用：
    // 用户再次点击「AI 解析」就是要重新生成，所以每次都调用模型并覆盖旧解析。
    long start = System.currentTimeMillis();
    try {
      AiClient.AiResult result = ai.chat("EXPLAIN", PromptTemplates.EXPLAIN_SYSTEM,
          PromptTemplates.explainUser(
              String.valueOf(question.get("content")),
              String.valueOf(question.get("options")),
              String.valueOf(question.get("answer"))),
          false);
      long duration = System.currentTimeMillis() - start;

      // 解析结果先返回给用户，落库交给 MQ 异步做；投递失败就同步写，保证结果不丢
      boolean queued = explanationQueue.publish(new ExplanationSaveEvent(
          questionId, userId, result.content(), result.model(),
          result.promptTokens(), result.completionTokens(), duration));
      if (!queued) {
        ai.logUsage(userId, "EXPLAIN", result.model(), questionId, result, null, duration);
        jdbc.update("update question set explanation = ? where id = ?",
            result.content(), questionId);
      }
      return Map.of("explanation", result.content());
    } catch (Exception e) {
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
