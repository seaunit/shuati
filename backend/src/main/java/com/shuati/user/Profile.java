package com.shuati.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "profiles")
public class Profile {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(length = 255)
  private String nickname;

  @Column(nullable = false, length = 16)
  private String role = "USER";

  @Column(nullable = false, length = 16)
  private String status = "ENABLED";

  @Column(name = "email_verified_at")
  private LocalDateTime emailVerifiedAt;

  @Column(name = "session_version", nullable = false)
  private int sessionVersion = 0;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;
}
