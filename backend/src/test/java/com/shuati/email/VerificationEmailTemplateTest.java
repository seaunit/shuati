package com.shuati.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationEmailTemplateTest {

  @Test
  void registerMailShowsTitleInstructionAndCode() {
    VerificationEmailTemplate.Rendered mail =
        VerificationEmailTemplate.render(EmailPurpose.REGISTER, "123456", 300);

    assertThat(mail.subject()).isEqualTo("你的拾题验证码");
    assertThat(mail.text()).contains("确认你的邮箱", "123456", "5 分钟内有效", "只能使用一次");
    assertThat(mail.html())
        .contains("确认你的邮箱", "123456", "5 分钟内有效")
        .contains("border-top:3px solid #2f6b45"); // 品牌绿描边
  }

  @Test
  void mailStaysPlainEnoughForSpamFilters() {
    VerificationEmailTemplate.Rendered mail =
        VerificationEmailTemplate.render(EmailPurpose.REGISTER, "654321", 300);

    // 事务邮件不放链接、不用图片，避免被反垃圾规则扣分
    assertThat(mail.html()).doesNotContain("http://", "https://", "<img", "<a ");
    assertThat(mail.subject()).doesNotContain("【", "】");
  }

  @Test
  void resetAndDeleteUseTheirOwnCopy() {
    assertThat(VerificationEmailTemplate.render(EmailPurpose.RESET, "111111", 300)
        .html()).contains("重置你的密码", "「找回密码」");
    assertThat(VerificationEmailTemplate.render(EmailPurpose.DELETE_BANK, "222222", 300)
        .html()).contains("确认删除题库");
  }

  @Test
  void ttlIsRoundedToMinutes() {
    assertThat(VerificationEmailTemplate.render(EmailPurpose.REGISTER, "333333", 90)
        .text()).contains("2 分钟内有效");
  }
}
