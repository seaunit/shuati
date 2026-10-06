package com.shuati.billing;

import com.shuati.common.ApiResponse;
import com.shuati.common.CurrentUser;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillingController {

  private final BillingService billingService;
  private final PointAccountService points;

  @GetMapping("/api/plans")
  public ApiResponse<Map<String, Object>> plans() {
    return ApiResponse.ok(billingService.catalog());
  }

  @GetMapping("/api/points")
  public ApiResponse<Map<String, Object>> points() {
    String userId = CurrentUser.id();
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("entitlements", points.entitlements(userId));
    result.put("ledger", points.ledger(userId, 50));
    result.put("rules", points.priceRules());
    return ApiResponse.ok(result);
  }

  @GetMapping("/api/orders")
  public ApiResponse<List<Map<String, Object>>> orders() {
    return ApiResponse.ok(billingService.orders(CurrentUser.id()));
  }

  @PostMapping("/api/orders")
  public ApiResponse<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
    String kind = body.get("kind") == null ? null : String.valueOf(body.get("kind"));
    String itemCode = body.get("itemCode") == null ? null : String.valueOf(body.get("itemCode"));
    String period = body.get("period") == null ? null : String.valueOf(body.get("period"));
    return ApiResponse.ok(billingService.createOrder(CurrentUser.id(), kind, itemCode, period));
  }
}
