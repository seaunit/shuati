package com.shuati;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.shuati.email.EmailVerificationProperties;

@EnableAsync
@EnableConfigurationProperties(EmailVerificationProperties.class)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ShuatiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ShuatiApplication.class, args);
  }
}
