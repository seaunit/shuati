package com.shuati.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MySQL JSON 列通过 JDBC 读出来是字符串，这里统一转成对象；
 * 同时把 Timestamp 转成带 Z 的 UTC ISO 字符串，前端 new Date() 才能正确解析。
 */
@Component
@RequiredArgsConstructor
public class Json {

  private final ObjectMapper objectMapper;

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  public Object parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(value, Object.class);
    } catch (Exception e) {
      return null;
    }
  }

  public Map<String, Object> normalize(Map<String, Object> row, Set<String> jsonColumns) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      Object value = entry.getValue();
      value = iso(value);
      if (jsonColumns.contains(entry.getKey())) {
        value = parse(value == null ? null : String.valueOf(value));
      }
      out.put(entry.getKey(), value);
    }
    return out;
  }

  /** 把 JDBC 返回的时间类型统一成带 Z 的 UTC ISO 字符串。 */
  public Object iso(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().toString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toInstant(ZoneOffset.UTC).toString();
    }
    if (value instanceof LocalDate localDate) {
      return localDate.toString();
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate().toString();
    }
    return value;
  }

  public List<Map<String, Object>> normalize(
      List<Map<String, Object>> rows, Set<String> jsonColumns) {
    return rows.stream().map(row -> normalize(row, jsonColumns)).toList();
  }
}
