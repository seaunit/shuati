package com.shuati.auth;

import com.shuati.user.Profile;
import com.shuati.user.ProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  public static final String COOKIE_NAME = "shuati_token";

  private final JwtService jwtService;
  private final ProfileRepository profiles;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = readToken(request);
      if (token != null) {
        Optional<JwtService.JwtPayload> payload = jwtService.parse(token);
        if (payload.isPresent()) {
          authenticate(request, payload.get());
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, JwtService.JwtPayload payload) {
    Optional<Profile> profileOpt = profiles.findById(payload.userId());
    if (profileOpt.isEmpty()) {
      return;
    }
    Profile profile = profileOpt.get();
    if ("DISABLED".equals(profile.getStatus())
        || payload.sessionVersion() != profile.getSessionVersion()) {
      return;
    }
    String role = payload.role() == null ? profile.getRole() : payload.role();
    var authentication = new UsernamePasswordAuthenticationToken(
        payload.userId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private String readToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
