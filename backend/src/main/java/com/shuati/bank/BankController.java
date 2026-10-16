package com.shuati.bank;

import com.shuati.common.ApiResponse;
import com.shuati.rate.RateLimit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BankController {

  private final BankService bankService;

  @GetMapping("/api/banks")
  public ApiResponse<List<Map<String, Object>>> banks() {
    return ApiResponse.ok(bankService.listBanks());
  }

  @GetMapping("/api/banks/{id}/units")
  public ApiResponse<List<Map<String, Object>>> units(@PathVariable long id) {
    return ApiResponse.ok(bankService.listUnits(id));
  }

  @GetMapping("/api/banks/{id}/questions")
  public ApiResponse<List<Map<String, Object>>> bankQuestions(
      @PathVariable long id, @RequestParam(defaultValue = "seq") String mode) {
    return ApiResponse.ok(bankService.listBankQuestions(id, mode));
  }

  @GetMapping("/api/units/{id}/questions")
  public ApiResponse<List<Map<String, Object>>> unitQuestions(
      @PathVariable long id, @RequestParam(defaultValue = "seq") String mode) {
    return ApiResponse.ok(bankService.listUnitQuestions(id, mode));
  }

  @GetMapping("/api/questions")
  public ApiResponse<List<Map<String, Object>>> allQuestions(
      @RequestParam(defaultValue = "seq") String mode) {
    return ApiResponse.ok(bankService.listAllQuestions(mode));
  }

  @GetMapping("/api/progress/bank/{id}")
  public ApiResponse<Map<String, Object>> bankProgress(@PathVariable long id) {
    return ApiResponse.ok(bankService.bankProgress(id));
  }

  @GetMapping("/api/progress/unit/{id}")
  public ApiResponse<Map<String, Object>> unitProgress(@PathVariable long id) {
    return ApiResponse.ok(bankService.unitProgress(id));
  }

  @GetMapping("/api/progress/all")
  public ApiResponse<Map<String, Object>> allProgress() {
    return ApiResponse.ok(bankService.allProgress());
  }

  /** 读练习光标（当前停在哪道题）；没记过就返回 null，客户端回退到「第一道没答过的题」 */
  @GetMapping("/api/progress/cursor")
  public ApiResponse<Map<String, Object>> cursor(
      @RequestParam(defaultValue = "ALL") String scopeType,
      @RequestParam(defaultValue = "0") long scopeId) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("questionId", bankService.cursor(scopeType, scopeId));
    return ApiResponse.ok(result);
  }

  /** 写/清练习光标：questionId 传 null 表示清空（一轮做完） */
  @PutMapping("/api/progress/cursor")
  @RateLimit(name = "progress:cursor", limit = 240, windowSeconds = 60,
      scope = RateLimit.Scope.USER)
  public ApiResponse<Void> saveCursor(@RequestBody Map<String, Object> body) {
    Object raw = body.get("questionId");
    Long questionId = raw == null ? null : Long.valueOf(String.valueOf(raw));
    long scopeId = body.get("scopeId") == null
        ? 0L
        : Long.parseLong(String.valueOf(body.get("scopeId")));
    bankService.saveCursor(
        body.get("scopeType") == null ? "ALL" : String.valueOf(body.get("scopeType")),
        scopeId,
        questionId);
    return ApiResponse.ok(null);
  }
}
