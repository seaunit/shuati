package com.shuati.email;

public interface EmailSender {

  void sendCode(String email, String code, EmailPurpose purpose);
}
