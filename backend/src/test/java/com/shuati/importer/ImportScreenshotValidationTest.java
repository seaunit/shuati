package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuati.common.ApiException;
import org.junit.jupiter.api.Test;

class ImportScreenshotValidationTest {

  @Test
  void acceptsPngAndJpegDataUrls() {
    assertThatCode(() -> ImportService.validateScreenshot("data:image/png;base64,iVBORw0KGgo="))
        .doesNotThrowAnyException();
    assertThatCode(() -> ImportService.validateScreenshot("data:image/jpeg;base64,/9j/4AAQ"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsNonImageAndNonDataUrl() {
    assertThatThrownBy(() -> ImportService.validateScreenshot("iVBORw0KGgo="))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("图片格式不正确");
    assertThatThrownBy(() -> ImportService.validateScreenshot("data:application/pdf;base64,AAA"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsOversizeImage() {
    String huge = "data:image/png;base64," + "A".repeat(7_000_000);
    assertThatThrownBy(() -> ImportService.validateScreenshot(huge))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("5MB");
  }
}
