package com.shuati.practice;

import com.shuati.common.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PracticeController {

  private final PracticeService practiceService;
  private final AiGradingService aiGradingService;

  @PostMapping("/api/practice/submit-choice")
  public ApiResponse<Map<String, Object>> submitChoice(@RequestBody Map<String, Object> body) {
    long questionId = Long.parseLong(String.valueOf(body.get("questionId")));
    Integer durationMs = body.get("durationMs") == null
        ? null : (int) Double.parseDouble(String.valueOf(body.get("durationMs")));
    return ApiResponse.ok(practiceService.submitChoice(questionId, body.get("selected"), durationMs));
  }

  @GetMapping("/api/practice/sessions")
  public ApiResponse<List<Map<String, Object>>> sessions() {
    return ApiResponse.ok(practiceService.listSessions());
  }

  @PostMapping("/api/practice/sessions")
  public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(practiceService.createSession(body));
  }

  @PatchMapping("/api/practice/sessions/{id}")
  public ApiResponse<Void> patchSession(@PathVariable long id, @RequestBody Map<String, Object> body) {
    practiceService.patchSession(id, body);
    return ApiResponse.ok(null);
  }

  @GetMapping("/api/wrong-book")
  public ApiResponse<List<Map<String, Object>>> wrongBook() {
    return ApiResponse.ok(practiceService.wrongBook());
  }

  @GetMapping("/api/stats/me")
  public ApiResponse<Map<String, Object>> statsMe() {
    return ApiResponse.ok(practiceService.statsMe());
  }

  @PostMapping("/api/practice/records/{id}/self-eval")
  public ApiResponse<Void> selfEval(@PathVariable long id, @RequestBody Map<String, Object> body) {
    practiceService.selfEval(id, body.get("verdict"));
    return ApiResponse.ok(null);
  }

  @PostMapping("/api/practice/submit-essay")
  public ApiResponse<Map<String, Object>> submitEssay(@RequestBody Map<String, Object> body) {
    long questionId = Long.parseLong(String.valueOf(body.get("questionId")));
    String answer = body.get("answer") == null ? "" : String.valueOf(body.get("answer")).trim();
    if (answer.isEmpty()) {
      throw new com.shuati.common.ApiException(400, "请先作答");
    }
    Integer durationMs = body.get("durationMs") == null
        ? null : (int) Double.parseDouble(String.valueOf(body.get("durationMs")));
    return ApiResponse.ok(aiGradingService.submitEssay(
        com.shuati.common.CurrentUser.id(), questionId, answer, durationMs));
  }

  @GetMapping("/api/practice/questions/{id}/explanation")
  public ApiResponse<Map<String, Object>> explanation(
      @PathVariable long id,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String selected,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean correct) {
    return ApiResponse.ok(aiGradingService.explanation(
        com.shuati.common.CurrentUser.id(), id, selected, correct));
  }
}
