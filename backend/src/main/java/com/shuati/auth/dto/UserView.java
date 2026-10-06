package com.shuati.auth.dto;

import com.shuati.user.Profile;

public record UserView(String id, String email, String nickname, String role, String status) {

  public static UserView from(Profile profile) {
    return new UserView(
        profile.getId(),
        profile.getEmail(),
        profile.getNickname(),
        profile.getRole(),
        profile.getStatus());
  }
}
