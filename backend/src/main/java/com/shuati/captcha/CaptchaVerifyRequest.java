package com.shuati.captcha;

public record CaptchaVerifyRequest(String ticket, String userInputCode) {
}
