package com.shuati.payment;

import com.shuati.common.ApiException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 与 Node 支付侧车通信。
 *
 * <p>为什么需要侧车：{@code @waffo/pancake-ts} 是 Node/TypeScript SDK，
 * Java 后端无法直接使用；而它的私钥绝不能下发到浏览器。因此由 Node 服务持有私钥并做
 * RSA-SHA256 签名，Java 只通过内网共享密钥调用它。
 */
@Slf4j
@Service
public class PaymentGateway {

  private final String baseUrl;
  private final String internalSecret;
  private final String currency;
  private final String subscriptionCurrency;
  private final boolean enabled;

  public PaymentGateway(
      @Value("${shuati.payments.enabled:false}") boolean enabled,
      @Value("${shuati.payments.base-url:}") String baseUrl,
      @Value("${shuati.payments.internal-secret:}") String internalSecret,
      @Value("${shuati.payments.currency:CNY}") String currency,
      @Value("${shuati.payments.subscription-currency:USD}") String subscriptionCurrency) {
    this.enabled = enabled && baseUrl != null && !baseUrl.isBlank();
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    this.internalSecret = internalSecret == null ? "" : internalSecret;
    this.currency = currency;
    this.subscriptionCurrency = subscriptionCurrency;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** 供 Java 内部拼 Waffo 商品编码：PLAN 带周期，PACK 不带 */
  public static String itemCodeOf(String kind, String itemCode, String period) {
    return "PLAN".equals(kind)
        ? "PLAN:" + itemCode + ":" + period
        : "PACK:" + itemCode;
  }

  /**
   * Waffo 平台限制：订阅（recurring）不支持 CNY，一次性支付支持。
   * 所以订阅走 subscription-currency（默认 USD），点数包走 currency（默认 CNY）。
   */
  public String currencyFor(String kind) {
    return "PLAN".equals(kind) ? subscriptionCurrency : currency;
  }

  public CheckoutSession createCheckout(
      String orderId,
      String waffoItemCode,
      String currency,
      String buyerIdentity,
      String buyerEmail,
      String amount) {
    if (!enabled) {
      throw new ApiException(400, "在线支付未启用");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", orderId);
    body.put("itemCode", waffoItemCode);
    body.put("currency", currency);
    body.put("buyerIdentity", buyerIdentity);
    body.put("buyerEmail", buyerEmail);
    if (amount != null && !amount.isBlank()) {
      body.put("amount", amount);
    }

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(30));
    try {
      Map<?, ?> response = RestClient.builder().requestFactory(factory).build()
          .post()
          .uri(baseUrl + "/checkout")
          .header("X-Internal-Secret", internalSecret)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      if (response == null) {
        throw new ApiException(502, "支付服务无响应");
      }
      Object url = response.get("checkoutUrl");
      Object sessionId = response.get("sessionId");
      if (url == null) {
        throw new ApiException(502, "支付服务未返回收银台地址");
      }
      return new CheckoutSession(
          String.valueOf(url), sessionId == null ? null : String.valueOf(sessionId));
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.warn("创建收银台失败：{}", e.getMessage());
      throw new ApiException(502, "创建收银台失败，请稍后再试");
    }
  }

  public record CheckoutSession(String checkoutUrl, String sessionId) {
  }
}
