package com.shuati.user;

import com.shuati.auth.dto.UserView;
import com.shuati.common.ApiException;
import com.shuati.common.ApiResponse;
import com.shuati.common.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

  private final ProfileRepository profiles;

  @GetMapping("/api/me")
  public ApiResponse<UserView> me() {
    Profile profile = profiles.findById(CurrentUser.id())
        .orElseThrow(() -> new ApiException(403, "账号资料不存在"));
    if ("DISABLED".equals(profile.getStatus())) {
      throw new ApiException(403, "账号已被禁用");
    }
    return ApiResponse.ok(UserView.from(profile));
  }
}
