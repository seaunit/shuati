package com.shuati.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析结果异步落库：投递到 Redis Stream 后，由消费者写库，而不是在用户请求里写。
 *
 * <p>注意 {@code AiExplanationBillingTest} 跑在队列关闭的配置下（enabled=false），
 * 走的是「投递不可用时退化为同步写库」那条路径，两个用例合起来覆盖了两条分支。
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ExplanationQueueTest {

  @DynamicPropertySource
  static void enableQueue(DynamicPropertyRegistry registry) {
    registry.add("shuati.explain-queue.enabled", () -> "true");
    // 把轮询间隔拉长，避免定时器与用例手动调用 poll() 抢消息
    registry.add("shuati.explain-queue.poll-ms", () -> "3600000");
  }

  @Autowired ExplanationSaveQueue queue;
  @Autowired ExplanationSaveConsumer consumer;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;

  @BeforeEach
  void clearStream() {
    redis.delete(ExplanationSaveQueue.STREAM);
  }

  @Test
  void publishedExplanationIsPersistedByConsumer() {
    long questionId = createQuestion();
    String userId = createUser();

    assertThat(queue.publish(new ExplanationSaveEvent(
        questionId, userId, "异步写入的解析", "test-model", 11, 22, 33))).isTrue();

    // 投递之后、消费之前：库里还没有写入，说明确实没在请求路径里落库
    assertThat(explanationOf(questionId)).isNull();

    consumer.poll();

    assertThat(explanationOf(questionId)).isEqualTo("异步写入的解析");
    assertThat(jdbc.queryForObject("""
        select count(*) from ai_call_log where question_id = ? and purpose = 'EXPLAIN'
        """, Integer.class, questionId)).isEqualTo(1);
  }

  private String explanationOf(long questionId) {
    return jdbc.queryForObject(
        "select explanation from question where id = ?", String.class, questionId);
  }

  private String createUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        insert into profiles (id, email, password_hash, role, status)
        values (?, ?, 'x', 'USER', 'ENABLED')
        """, id, "queue-" + id.substring(0, 8) + "@example.com");
    return id;
  }

  private long createQuestion() {
    String bankName = "队列测试-" + UUID.randomUUID();
    jdbc.update("insert into bank (name, is_default, owner_id) values (?, 0, null)", bankName);
    Long bankId = jdbc.queryForObject("select id from bank where name = ?", Long.class, bankName);
    jdbc.update("insert into unit (bank_id, name, sort) values (?, '队列单元', 1)", bankId);
    Long unitId = jdbc.queryForObject(
        "select id from unit where bank_id = ?", Long.class, bankId);
    jdbc.update("""
        insert into question
          (unit_id, type, content, options, answer, key_points, difficulty, status)
        values (?, 'SINGLE', '队列题干', cast('[{"key":"A","text":"选项A"}]' as json),
                'A', cast('[]' as json), 'EASY', 'ON')
        """, unitId);
    return jdbc.queryForObject(
        "select id from question where unit_id = ?", Long.class, unitId);
  }
}
