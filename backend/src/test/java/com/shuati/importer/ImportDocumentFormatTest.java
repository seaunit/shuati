package com.shuati.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@SpringBootTest
class ImportDocumentFormatTest {

  @Autowired ImportService importService;

  @Test
  void docExtensionWithOoxmlContentUsesOoxmlParser() throws Exception {
    String body = "Java 面试题：解释 HashMap 的扩容机制。";
    Map<String, Object> task = new LinkedHashMap<>();
    task.put("kind", "doc");
    task.put("source_name", "java-questions.doc");
    task.put("source_text", Base64.getEncoder().encodeToString(createDocx(body)));

    String text = ReflectionTestUtils.invokeMethod(importService, "sourceTextOf", task);

    assertThat(text).contains(body);
  }

  private byte[] createDocx(String text) throws Exception {
    try (XWPFDocument document = new XWPFDocument();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.createParagraph().createRun().setText(text);
      document.write(output);
      return output.toByteArray();
    }
  }
}
