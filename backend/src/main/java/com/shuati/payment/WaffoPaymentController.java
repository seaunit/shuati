package com.shuati.payment;

import com.shuati.billing.BillingService;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Waffo 回调转发入口：由 Node 支付侧车验签后调用，用共享密钥鉴权，不走 JWT。
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class WaffoPaymentController {

  /** 只有这些事件代表「钱已到账」，需要发货 */
  private static final List<String> PAID_EVENTS = List.of(
      "order.completed",
      "subscription.activated",
      "subscription.payment_succeeded");

  /** 退款成功：需要扣回该订单发放的点数 */
  private static final String REFUND_SUCCEEDED = "refund.succeeded";

  private final BillingService billingService;

  @Value("${shuati.payments.internal-secret:}")
  private String internalSecret;

  @PostMapping("/waffo/notify")
  public ApiResponse<Void> notify(
      @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
      @RequestBody Map<String, Object> body) {
    if (internalSecret.isBlank() || !internalSecret.equals(secret)) {
      throw new ApiException(401, "unauthorized");
    }
    String orderId = String.valueOf(body.getOrDefault("orderId", ""));
    String eventType = String.valueOf(body.getOrDefault("eventType", ""));
    if (orderId.isBlank()) {
      throw new ApiException(400, "缺少 orderId");
    }
    if (REFUND_SUCCEEDED.equals(eventType)) {
      Map<String, Object> result = billingService.refundOrder(
          orderId, centsOf(body.get("refundedAmount")), stringOf(body.get("refundReason")));
      log.info("Waffo 退款完成，订单已标记退款并扣回点数：{} {}", orderId, result);
      return ApiResponse.ok(null);
    }
    if (!PAID_EVENTS.contains(eventType)) {
      // 退款、取消、逾期等事件只记录，不在这里发货
      log.info("收到无需发货的支付事件：{} order={}", eventType, orderId);
      return ApiResponse.ok(null);
    }
    // 幂等：重复投递同一个事件时直接返回成功
    billingService.settleOrder(orderId, "PAID", true);
    log.info("Waffo 支付到账，订单已结算：{} {}", orderId, eventType);
    return ApiResponse.ok(null);
  }

  /** "9.00" -> 900。解析不了就返回 null（只影响对账展示，不影响扣点）。 */
  private Integer centsOf(Object amount) {
    if (amount == null) {
      return null;
    }
    String raw = String.valueOf(amount).trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return new java.math.BigDecimal(raw)
          .movePointRight(2)
          .setScale(0, java.math.RoundingMode.HALF_UP)
          .intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      log.warn("无法解析退款金额：{}", raw);
      return null;
    }
  }

  private String stringOf(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
