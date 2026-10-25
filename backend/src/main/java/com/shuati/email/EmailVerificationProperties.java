package com.shuati.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shuati.email")
public record EmailVerificationProperties(
    boolean enabled,
    boolean simulate,
    String from,
    String fromName,
    String secret,
    int codeTtlSeconds,
    int cooldownSeconds,
    int maxAttempts,
    int emailDailyLimit,
    int ipHourlyLimit,
    int deviceDailyLimit,
    int globalDailyLimit) {
}
