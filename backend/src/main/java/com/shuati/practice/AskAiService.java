package com.shuati.practice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuati.abuse.NewAccountAiLimit;
import com.shuati.ai.AiCallLogger;
import com.shuati.ai.AiWorkflowInputs;
import com.shuati.ai.AiWorkflowKey;
import com.shuati.ai.AiWorkflowPort;
import com.shuati.ai.AiWorkflowResult;
import com.shuati.bank.BankService;
import com.shuati.billing.PointAccountService;
import com.shuati.common.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AskAiService {

  private static final int MAX_QUESTION_LENGTH = 1000;
  private static final int MAX_HISTORY_MESSAGES = 20;
  private static final int MAX_HISTORY_ITEM_LENGTH = 4000;
  private static final int MAX_HISTORY_TOTAL_LENGTH = 12000;
  private static final String PROCESSING = "__PROCESSING__";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);
  private static final Pattern REQUEST_ID =
      Pattern.compile("[A-Za-z0-9-]{8,64}");

  private final BankService bankService;
  private final AiWorkflowPort ai;
  private final AiCallLogger aiCallLogger;
  private final ObjectMapper objectMapper;
  private final PointAccountService points;
  private final NewAccountAiLimit newAccountAiLimit;
  private final StringRedisTemplate redis;

  public Map<String, Object> ask(
      String userId,
      long questionId,
      String rawQuestion,
      List<?> rawHistory,
      String rawRequestId) {
    String question = normalizeQuestion(rawQuestion);
    String requestId = normalizeRequestId(rawRequestId);
    Map<String, Object> questionContext = bankService.questionForAi(questionId);

    String key = idempotencyKey(userId, requestId);
    Map<String, Object> cached = cachedResult(key);
    if (cached != null) {
      return cached;
    }

    String conversation = serializeHistory(rawHistory);
    newAccountAiLimit.check(userId);
    if (!acquire(key)) {
      cached = cachedResult(key);
      if (cached != null) {
        return cached;
      }
      throw new ApiException(409, "问 AI 请求正在处理中，请勿重复提交");
    }

    int chargedCost = 0;
    boolean aiAttempted = false;
    long started = System.currentTimeMillis();
    AiWorkflowResult result = null;
    try {
      Map<String, Object> charged = points.consumePoints(
          userId, "ASK", null, "question", String.valueOf(questionId));
      if (!Boolean.TRUE.equals(charged.get("ok"))) {
        throw new ApiException(402, "AI 点数不足：本次需要 " + charged.get("cost")
            + " 点，当前可用 " + charged.get("available")
            + " 点。可在「套餐与点数」升级套餐或购买加量包。");
      }
      chargedCost = intValue(charged.get("cost"));

      aiAttempted = true;
      result = ai.ask(userId, new AiWorkflowInputs.AskInput(
          string(questionContext.get("content")),
          string(questionContext.get("options")),
          string(questionContext.get("answer")),
          string(questionContext.get("explanation")),
          conversation,
          question));
      String answer = result.content() == null ? "" : result.content().trim();
      if (answer.isEmpty()) {
        throw new ApiException(502, "AI 返回内容为空");
      }

      aiCallLogger.success(userId, "ASK", questionId, result);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("answer", answer);
      response.put("cost", chargedCost);
      response.put("available", charged.get("available"));
      cache(key, response);
      return response;
    } catch (Exception e) {
      if (chargedCost > 0) {
        points.refundPoints(userId, chargedCost, "AI_ASK_FAILED",
            "question", String.valueOf(questionId));
      }
      if (aiAttempted) {
        aiCallLogger.failure(
            userId,
            "ASK",
            questionId,
            result == null ? ai.provider() : result.provider(),
            AiWorkflowKey.ASK,
            result == null ? null : result.externalRunId(),
            e.getMessage(),
            System.currentTimeMillis() - started);
      }
      release(key);
      if (e instanceof ApiException api) {
        if (api.getStatus() >= 500) {
          String reason = api.getMessage() == null || api.getMessage().isBlank()
              ? "未知错误" : api.getMessage();
          throw new ApiException(502, "问 AI 失败：" + reason);
        }
        throw api;
      }
      log.warn("问 AI 失败 questionId={}", questionId, e);
      throw new ApiException(502, "问 AI 失败：AI 服务暂不可用");
    }
  }

  private String normalizeQuestion(String value) {
    String question = value == null ? "" : value.trim();
    if (question.isEmpty()) {
      throw new ApiException(400, "请输入想追问的问题");
    }
    if (question.length() > MAX_QUESTION_LENGTH) {
      throw new ApiException(400, "问题最多 " + MAX_QUESTION_LENGTH + " 个字符");
    }
    return question;
  }

  private String normalizeRequestId(String value) {
    String requestId = value == null ? "" : value.trim();
    if (!REQUEST_ID.matcher(requestId).matches()) {
      throw new ApiException(400, "请求标识格式不正确");
    }
    return requestId;
  }

  private String serializeHistory(List<?> rawHistory) {
    if (rawHistory == null || rawHistory.isEmpty()) {
      return "[]";
    }
    if (rawHistory.size() > MAX_HISTORY_MESSAGES) {
      throw new ApiException(400, "本轮对话最多保留 " + MAX_HISTORY_MESSAGES + " 条消息");
    }
    List<Map<String, String>> history = new ArrayList<>();
    int totalLength = 0;
    for (Object rawItem : rawHistory) {
      if (!(rawItem instanceof Map<?, ?> item)) {
        throw new ApiException(400, "历史消息格式不正确");
      }
      String role = string(item.get("role")).toLowerCase();
      String content = string(item.get("content")).trim();
      if (!"user".equals(role) && !"assistant".equals(role)) {
        throw new ApiException(400, "历史消息角色不正确");
      }
      if (content.isEmpty()) {
        throw new ApiException(400, "历史消息内容不能为空");
      }
      if (content.length() > MAX_HISTORY_ITEM_LENGTH) {
        throw new ApiException(400, "单条历史消息最多 "
            + MAX_HISTORY_ITEM_LENGTH + " 个字符");
      }
      totalLength += content.length();
      if (totalLength > MAX_HISTORY_TOTAL_LENGTH) {
        throw new ApiException(400, "本轮对话总长度最多 "
            + MAX_HISTORY_TOTAL_LENGTH + " 个字符");
      }
      history.add(Map.of("role", role, "content", content));
    }
    try {
      return objectMapper.writeValueAsString(history);
    } catch (Exception e) {
      throw new ApiException(400, "历史消息格式不正确");
    }
  }

  private Map<String, Object> cachedResult(String key) {
    try {
      String cached = redis.opsForValue().get(key);
      if (cached == null || PROCESSING.equals(cached)) {
        return null;
      }
      return objectMapper.readValue(cached, new TypeReference<>() {
      });
    } catch (Exception e) {
      log.warn("读取问 AI 幂等结果失败", e);
      return null;
    }
  }

  private boolean acquire(String key) {
    try {
      Boolean acquired = redis.opsForValue().setIfAbsent(key, PROCESSING, IDEMPOTENCY_TTL);
      return !Boolean.FALSE.equals(acquired);
    } catch (Exception e) {
      // Redis 故障不应阻断主流程；此时仅失去短窗口幂等保护。
      log.warn("问 AI 幂等键写入失败，本次放行", e);
      return true;
    }
  }

  private void cache(String key, Map<String, Object> response) {
    try {
      redis.opsForValue().set(
          key, objectMapper.writeValueAsString(response), IDEMPOTENCY_TTL);
    } catch (Exception e) {
      log.warn("写入问 AI 幂等结果失败", e);
    }
  }

  private void release(String key) {
    try {
      redis.delete(key);
    } catch (Exception e) {
      log.warn("释放问 AI 幂等键失败", e);
    }
  }

  private String idempotencyKey(String userId, String requestId) {
    return "ask:" + userId + ":" + requestId;
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }
}
