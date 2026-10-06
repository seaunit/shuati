package com.shuati.bank;

import com.shuati.common.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
