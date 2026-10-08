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
}
