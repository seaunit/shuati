package com.shuati.email;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailDispatchService {

  private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

  private final EmailVerificationService emails;

  @Async("emailExecutor")
  public void send(EmailVerificationService.PreparedCode prepared) {
    try {
      emails.sendPrepared(prepared);
    } catch (Exception e) {
      // 对外统一返回成功，异步失败只记录并清理当前验证码。
      log.warn("邮箱验证码异步发送失败：{}", e.getMessage());
    }
  }
}
