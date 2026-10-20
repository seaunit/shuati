package com.shuati.email;

/**
 * 验证码邮件的版式。
 *
 * <p>设计要点（兼顾观感与送达率）：
 * <ul>
 *   <li>纯 HTML + 内联样式 + table 布局，不引外部 CSS、不用图片、不放链接——
 *       图片和链接都是反垃圾规则的加分项，事务邮件越"干净"越好；</li>
 *   <li>品牌不用图片，用 CSS 圆角方块 + 一个「拾」字，客户端默认不加载图片也不影响；</li>
 *   <li>正文一眼能看到 6 位码，下面写清有效期和"只能使用一次"；</li>
 *   <li>同时输出纯文本版本，纯文本客户端与部分反垃圾检查看的是它。</li>
 * </ul>
 */
public final class VerificationEmailTemplate {

  private VerificationEmailTemplate() {
  }

  public record Rendered(String subject, String text, String html) {
  }

  public static Rendered render(EmailPurpose purpose, String code, int ttlSeconds) {
    String title;
    String instruction;
    switch (purpose) {
      case RESET -> {
        title = "重置你的密码";
        instruction = "请回到拾题的「找回密码」页，输入下面的 6 位验证码继续。";
      }
      case DELETE_BANK -> {
        title = "确认删除题库";
        instruction = "请回到拾题，输入下面的 6 位验证码确认删除题库。";
      }
      default -> {
        title = "确认你的邮箱";
        instruction = "请回到拾题的注册页，输入下面的 6 位验证码完成确认。";
      }
    }
    int minutes = Math.max(1, Math.round(ttlSeconds / 60f));
    String validity = "验证码 " + minutes + " 分钟内有效，且只能使用一次。";
    String footer = "这是一封系统邮件，请勿直接回复。如果你没有发起此操作，可以忽略它。";

    String text = title + "\n\n" + instruction + "\n\n" + code + "\n\n" + validity
        + "\n\n" + footer + "\n";
    String html = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>拾题验证码</title>
        </head>
        <body style="margin:0;padding:0;background:#ffffff;">
          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                 style="background:#ffffff;">
            <tr>
              <td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="max-width:520px;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',Arial,sans-serif;">
                  <tr>
                    <td style="padding:0 0 20px 0;">
                      <table role="presentation" cellpadding="0" cellspacing="0">
                        <tr>
                          <td width="40" height="40" align="center" valign="middle"
                              style="width:40px;height:40px;background:#eef1ee;border-radius:12px;
                                     color:#2f6b45;font-size:20px;font-weight:700;line-height:40px;">拾</td>
                          <td style="padding-left:12px;font-size:18px;font-weight:600;color:#17181a;">拾题</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  <tr>
                    <td style="border:1px solid #e9ebee;border-top:3px solid #2f6b45;
                               border-radius:8px;background:#ffffff;padding:32px 28px;">
                      <p style="margin:0 0 10px 0;font-size:22px;line-height:1.4;font-weight:600;color:#17181a;">
                        %s
                      </p>
                      <p style="margin:0 0 24px 0;font-size:14px;line-height:1.7;color:#6b7280;">
                        %s
                      </p>
                      <p style="margin:0;text-align:center;font-size:38px;line-height:1.2;
                                letter-spacing:10px;font-weight:700;color:#17181a;
                                font-family:ui-monospace,Menlo,Consolas,'Courier New',monospace;">
                        %s
                      </p>
                      <p style="margin:22px 0 0 0;text-align:center;font-size:13px;line-height:1.6;color:#6b7280;">
                        %s
                      </p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:20px 4px 0 4px;font-size:12px;line-height:1.7;color:#8a8f98;">
                      %s
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(title, instruction, code, validity, footer);

    return new Rendered("你的拾题验证码", text, html);
  }
}
