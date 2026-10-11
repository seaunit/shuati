package com.shuati.practice;

import com.shuati.ai.AiClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 解析结果落库队列（消费者）。
 *
 * <p>把「写 question.explanation + 写 ai_call_log」从用户请求里挪出来，
 * 用户拿到的响应不必等落库。处理成功才 ACK；失败不 ACK，留在 pending 里下轮重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExplanationSaveConsumer {

  private static final String CONSUMER = "worker-1";
  private static final int BATCH = 20;

  private final StringRedisTemplate redis;
  private final JdbcTemplate jdbc;
  private final AiClient ai;
  private final ExplanationSaveQueue queue;

  @Scheduled(fixedDelayString = "${shuati.explain-queue.poll-ms:2000}")
  public void poll() {
    if (!queue.isEnabled()) {
      return;
    }
    ensureGroup();
    List<MapRecord<String, Object, Object>> records;
    try {
      records = redis.opsForStream().read(
          Consumer.from(ExplanationSaveQueue.GROUP, CONSUMER),
          StreamReadOptions.empty().count(BATCH),
          StreamOffset.create(ExplanationSaveQueue.STREAM, ReadOffset.lastConsumed()));
    } catch (Exception e) {
      log.warn("读取解析落库队列失败：{}", e.getMessage());
      return;
    }
    if (records == null || records.isEmpty()) {
      return;
    }
    for (MapRecord<String, Object, Object> record : records) {
      try {
        save(toStringMap(record.getValue()));
        redis.opsForStream().acknowledge(
            ExplanationSaveQueue.STREAM, ExplanationSaveQueue.GROUP, record.getId());
      } catch (Exception e) {
        // 故意不 ACK：留在 pending 里下轮重试，最多重复写一次同样的内容（幂等）
        log.warn("解析结果落库失败，等待重试 id={}：{}", record.getId(), e.getMessage());
      }
    }
  }

  private Map<String, String> toStringMap(Map<Object, Object> fields) {
    Map<String, String> result = new LinkedHashMap<>();
    fields.forEach((key, value) ->
        result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
    return result;
  }

  /** 真正干活的一步，单独抽出来是为了让测试可以直接调用，不必等定时器。 */
  public void save(Map<String, String> fields) {
    long questionId = Long.parseLong(fields.get("questionId"));
    String content = fields.getOrDefault("content", "");
    String model = fields.getOrDefault("model", "unknown");
    jdbc.update("update question set explanation = ? where id = ?", content, questionId);
    ai.logUsage(
        fields.get("userId"), "EXPLAIN", model, questionId,
        new AiClient.AiResult(
            content, model,
            intOf(fields.get("promptTokens")), intOf(fields.get("completionTokens")),
            intOf(fields.get("promptTokens")) + intOf(fields.get("completionTokens"))),
        null, longOf(fields.get("durationMs")));
  }

  /**
   * 消费者组不存在就建一个（从 stream 头部开始消费）。
   * 每次都试着建、只吞掉“已存在”的错误，避免缓存布尔标记在 stream 被清空后失效。
   */
  private void ensureGroup() {
    try {
      redis.opsForStream().createGroup(
          ExplanationSaveQueue.STREAM, ReadOffset.from("0"), ExplanationSaveQueue.GROUP);
    } catch (Exception e) {
      // BUSYGROUP：组已存在；其它异常（比如 Redis 暂时不可用）交给下一轮
      if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
        log.debug("创建消费者组跳过：{}", e.getMessage());
      }
    }
  }

  private int intOf(String value) {
    try {
      return value == null ? 0 : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private long longOf(String value) {
    try {
      return value == null ? 0L : Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
