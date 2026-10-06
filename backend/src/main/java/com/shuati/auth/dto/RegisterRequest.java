package com.shuati.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "请填写密码")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位") String password) {
}
