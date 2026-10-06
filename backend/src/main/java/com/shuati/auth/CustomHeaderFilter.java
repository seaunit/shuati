package com.shuati.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 同域 SPA 的 CSRF 防护：写请求必须带自定义头。
 * 跨站表单无法设置该头，跨域请求又会被浏览器预检拦下（服务端未开启 CORS），
 * 配合 JWT 的 SameSite=Lax Cookie 即可防住 CSRF。
 */
@Component
public class CustomHeaderFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Requested-With";
  public static final String VALUE = "ShuatiApp";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String method = request.getMethod();
    if (!List.of("GET", "HEAD", "OPTIONS").contains(method)
        && !VALUE.equals(request.getHeader(HEADER))) {
      response.setStatus(403);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(
          "{\"code\":403,\"message\":\"请求缺少安全校验头\",\"data\":null}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
