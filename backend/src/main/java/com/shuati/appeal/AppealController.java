package com.shuati.appeal;

import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.CurrentUser;
import com.shuati.common.Json;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AppealController {

  private final JdbcTemplate jdbc;
  private final Json json;

  @GetMapping("/api/appeals")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(json.normalize(jdbc.queryForList("""
        select * from appeal where user_id = ? order by created_at desc
        """, CurrentUser.id()), Set.of()));
  }

  @PostMapping("/api/appeals")
  public ApiResponse<Void> create(@RequestBody Map<String, Object> body) {
    long recordId = parseLong(body.get("practiceRecordId"));
    String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
    if (recordId == 0) {
      throw new ApiException(400, "缺少作答记录 ID");
    }
    if (reason.isEmpty()) {
      throw new ApiException(400, "请填写申诉原因");
    }
    Integer owned = jdbc.queryForObject(
        "select count(*) from practice_record where id = ? and user_id = ?",
        Integer.class, recordId, CurrentUser.id());
    if (owned == null || owned == 0) {
      throw new ApiException(404, "作答记录不存在");
    }
    Integer duplicated = jdbc.queryForObject(
        "select count(*) from appeal where practice_record_id = ?", Integer.class, recordId);
    if (duplicated != null && duplicated > 0) {
      throw new ApiException(400, "该记录已申诉");
    }
    jdbc.update("insert into appeal (practice_record_id, user_id, reason) values (?, ?, ?)",
        recordId, CurrentUser.id(), reason);
    return ApiResponse.ok(null);
  }

  private long parseLong(Object value) {
    if (value == null) {
      return 0;
    }
    try {
      return (long) Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
