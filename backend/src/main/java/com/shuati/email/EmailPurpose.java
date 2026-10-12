package com.shuati.email;

public enum EmailPurpose {
  REGISTER,
  RESET,
  /** 删除题库这类不可逆操作，验证码不能和注册/重置混用 */
  DELETE_BANK
}
