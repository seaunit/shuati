package com.shuati.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "请填写邮箱") String email,
    @NotBlank(message = "请填写密码") String password,
    String captchaTicket,
    String captchaCode) {
}
