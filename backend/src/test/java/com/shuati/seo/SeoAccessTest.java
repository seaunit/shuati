package com.shuati.seo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 蜘蛛抓的这些页面必须免登录，同时别的接口不能被顺带放开。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SeoAccessTest {

  @Autowired MockMvc mvc;

  @Test
  void seoPagesAreReachableWithoutLogin() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
    mvc.perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xml"));
    // 不存在的题库给 404 的 HTML，而不是未登录 401
    mvc.perform(get("/bank/999999999"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
  }

  @Test
  void businessApisStillNeedLogin() throws Exception {
    // 游客能看的接口保持开放
    mvc.perform(get("/api/banks")).andExpect(status().isOk());
    // 其他接口照旧要登录
    mvc.perform(get("/api/points")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/manage/banks")).andExpect(status().isUnauthorized());
  }
}
