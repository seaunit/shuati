package com.shuati.rate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流注解。放在 Controller 方法上即可，超限由拦截器统一抛 429。
 *
 * <pre>
 * &#64;RateLimit(name = "ai:judge", limit = 30, windowSeconds = 60, scope = Scope.USER)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** 维度名，会拼进 Redis key，例如 rate:ai:judge:user:{userId} */
  String name();

  /** 窗口内允许的最大次数 */
  int limit();

  /** 窗口长度（秒） */
  int windowSeconds() default 60;

  /** 限流维度 */
  Scope scope() default Scope.IP;

  enum Scope {
    /** 按客户端 IP */
    IP,
    /** 按登录用户（未登录时退化为 IP） */
    USER,
    /** 两者都限，先 IP 后用户 */
    IP_AND_USER
  }
}
