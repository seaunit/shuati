package com.shuati.practice;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 解析结果落库队列（生产者）。
 *
 * <p>用 Redis Streams 而不是 RabbitMQ / Kafka：项目本来就用 Redis 存验证码和限流，
 * 复用同一个实例即可，2 核 4G 的服务器上不用再养一个 broker。
 *
 * <p>Stream 带消费者组，语义是 at-least-once：消费者处理成功才 ACK，
 * 进程半路挂掉时未 ACK 的消息会在下轮重新投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExplanationSaveQueue {

  /** Redis Stream 名 */
  static final String STREAM = "shuati:stream:explanation-save";

  /** 消费者组名 */
  static final String GROUP = "explanation-save";

  private final StringRedisTemplate redis;

  @Value("${shuati.explain-queue.enabled:true}")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 投递一条落库事件。
   *
   * @return true=已入队；false=未入队（开关关闭或 Redis 异常），
   *     调用方必须退化为同步写库，否则解析结果会丢
   */
  public boolean publish(ExplanationSaveEvent event) {
    if (!enabled) {
      return false;
    }
    Map<String, String> body = new LinkedHashMap<>();
    body.put("questionId", String.valueOf(event.questionId()));
    body.put("userId", event.userId());
    body.put("content", event.explanation());
    body.put("model", event.model());
    body.put("promptTokens", String.valueOf(event.promptTokens()));
    body.put("completionTokens", String.valueOf(event.completionTokens()));
    body.put("durationMs", String.valueOf(event.durationMs()));
    try {
      redis.opsForStream().add(MapRecord.create(STREAM, body));
      return true;
    } catch (Exception e) {
      log.warn("解析结果投递 MQ 失败，本次退化为同步写库：{}", e.getMessage());
      return false;
    }
  }
}
