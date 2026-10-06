package com.shuati.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey key;
  private final long ttlSeconds;

  public JwtService(
      @Value("${shuati.jwt.secret}") String secret,
      @Value("${shuati.jwt.ttl-seconds:604800}") long ttlSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttlSeconds = ttlSeconds;
  }

  public String issue(String userId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(key)
        .compact();
  }

  public Optional<JwtPayload> parse(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
      return Optional.of(new JwtPayload(claims.getSubject(), claims.get("role", String.class)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public record JwtPayload(String userId, String role) {
  }
}
