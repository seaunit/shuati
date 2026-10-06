package com.shuati.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, String> {

  Optional<Profile> findByEmail(String email);
}
