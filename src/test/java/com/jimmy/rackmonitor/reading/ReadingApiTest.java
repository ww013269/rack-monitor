package com.jimmy.rackmonitor.reading;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 整合測試：啟動 Spring context，透過 MockMvc 打 API。 */
@SpringBootTest(properties = {"api.key=test-key", "rack.threshold=27.0"})
class ReadingApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 寫入資料後可查到最新一筆() throws Exception {
        mvc.perform(post("/api/readings")
                        .header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rackId":"R-10","powerKw":5.1,"inletTemp":30.2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ALERT"));

        mvc.perform(get("/api/racks/R-10/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inletTemp").value(30.2));
    }

    @Test
    void 沒帶金鑰回401() throws Exception {
        mvc.perform(post("/api/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rackId":"R-11","powerKw":5.1,"inletTemp":22.0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 欄位不合法回400() throws Exception {
        mvc.perform(post("/api/readings")
                        .header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rackId":"","powerKw":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 查不存在的機櫃回404() throws Exception {
        mvc.perform(get("/api/racks/NOPE/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void info顯示版本與環境() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("local"))
                .andExpect(jsonPath("$.env").value("local"));
    }
}
