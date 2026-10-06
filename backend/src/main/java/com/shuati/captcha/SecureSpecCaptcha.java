package com.shuati.captcha;

import com.wf.captcha.SpecCaptcha;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import javax.imageio.ImageIO;

/**
 * 基于 EasyCaptcha 的 SecureSpecCaptcha。
 *
 * <p>为什么还要重写：
 * <ul>
 *   <li>{@code alphas()}：EasyCaptcha 默认字符集含 0/O、1/l/I 等易混淆字符，这里换成安全字符集；</li>
 *   <li>{@code out()}：EasyCaptcha 1.6.2 的 SpecCaptcha 只有干扰线和圆、没有字符旋转，
 *       这里补上随机旋转、随机字号与基线偏移，满足国标对“字符扭曲”的要求。</li>
 * </ul>
 * 绘制仍复用 EasyCaptcha 提供的 {@code color()} / {@code drawLine()} / {@code drawOval()} /
 * {@code drawBesselLine()} 工具方法。
 */
public class SecureSpecCaptcha extends SpecCaptcha {

  /** 剔除 0/O、1/l/I 等易混淆字符 */
  private static final String SAFE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Font FALLBACK_FONT = new Font("Arial", Font.BOLD, 28);

  public SecureSpecCaptcha(int width, int height, int len) {
    super(width, height, len);
  }

  @Override
  protected char[] alphas() {
    char[] chars = new char[len];
    for (int i = 0; i < len; i++) {
      chars[i] = SAFE_CHARS.charAt(RANDOM.nextInt(SAFE_CHARS.length()));
    }
    this.chars = new String(chars);
    return chars;
  }

  @Override
  public boolean out(OutputStream os) {
    char[] chars = textChar();
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(new Color(250, 247, 242));
      g.fillRect(0, 0, width, height);

      // 噪点
      for (int i = 0; i < width / 2; i++) {
        g.setColor(color(160, 230));
        g.fillOval(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
      }

      // 干扰线、干扰圆、贝塞尔曲线
      drawLine(3 + RANDOM.nextInt(3), g);
      drawOval(2, g);
      drawBesselLine(2, g);

      // 字符：随机旋转 + 随机字号 + 随机基线偏移
      Font base = getFont() == null ? FALLBACK_FONT : getFont();
      int step = width / (chars.length + 1);
      for (int i = 0; i < chars.length; i++) {
        int fontSize = (int) (height * (0.62 + RANDOM.nextDouble() * 0.18));
        int x = step * (i + 1) - step / 2;
        int y = (int) (height * 0.72) + RANDOM.nextInt(8) - 4;
        double angle = (RANDOM.nextDouble() - 0.5) * 0.7;

        AffineTransform origin = g.getTransform();
        g.setFont(base.deriveFont(Font.BOLD, fontSize));
        g.setColor(color(20, 120));
        g.rotate(angle, x, y);
        g.drawString(String.valueOf(chars[i]), x, y);
        g.setTransform(origin);
      }
      g.dispose();
      ImageIO.write(image, "png", os);
      os.flush();
      return true;
    } catch (IOException e) {
      g.dispose();
      return false;
    }
  }
}
