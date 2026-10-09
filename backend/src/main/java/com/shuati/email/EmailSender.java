package com.shuati.email;

public interface EmailSender {

  void sendRegisterCode(String email, String code);
}
