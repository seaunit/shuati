package com.shuati.captcha;

/** 只返回票据与图片，绝不返回验证码答案。 */
public record CaptchaImageResponse(String ticket, String imageBase64, int expiresInSeconds) {
}
