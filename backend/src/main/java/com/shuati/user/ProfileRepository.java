package com.shuati.user;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, String> {

  Optional<Profile> findByEmail(String email);

  @Modifying
  @Query("update Profile p set p.lastLoginAt = :lastLoginAt where p.id = :id")
  int updateLastLoginAt(
      @Param("id") String id,
      @Param("lastLoginAt") LocalDateTime lastLoginAt);
}
