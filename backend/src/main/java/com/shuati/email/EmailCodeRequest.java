package com.shuati.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailCodeRequest(
    @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "请填写图形验证码") String captchaTicket,
    @NotBlank(message = "请填写图形验证码") String captchaCode) {
}
