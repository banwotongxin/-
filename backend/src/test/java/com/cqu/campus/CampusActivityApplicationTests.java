package com.cqu.campus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 启动与核心链路冒烟测试（H2 内存库）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusActivityApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void loginAndBrowseActivities() throws Exception {
        String token = login("admin", "123456");

        mockMvc.perform(get("/api/activities")
                        .header("Authorization", "Bearer " + token)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void statisticsOverviewAndTrend() throws Exception {
        String token = login("admin", "123456");
        String from = LocalDateTime.now().minusDays(30).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String to = LocalDateTime.now().plusDays(1).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ruleVersion").value("STAT-V1"))
                .andExpect(jsonPath("$.data.scopeType").value("ALL"))
                .andExpect(jsonPath("$.data.activityCount").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.signupCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.distinctStudentCount").value(lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.approvedCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.checkinCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.clubRanking").isArray())
                .andExpect(jsonPath("$.data.clubRanking[0].activityScore").exists());

        mockMvc.perform(get("/api/statistics/trend")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ruleVersion").value("STAT-V1"))
                .andExpect(jsonPath("$.data.points").isArray())
                .andExpect(jsonPath("$.data.points[0].date").exists());
    }

    @Test
    void statisticsRespectsClubAdminScope() throws Exception {
        String token = login("clubadmin", "123456");
        String from = LocalDateTime.now().minusDays(30).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String to = LocalDateTime.now().plusDays(1).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scopeType").value("MANAGED"))
                .andExpect(jsonPath("$.data.clubNames[0]").value("篮球社"));

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from)
                        .param("to", to)
                        .param("clubId", "999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        String studentToken = login("student", "123456");
        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void statisticsRejectsInvalidRange() throws Exception {
        String token = login("admin", "123456");

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-20T00:00:00")
                        .param("to", "2026-11-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_RANGE_INVALID"));

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_RANGE_INVALID"));

        mockMvc.perform(get("/api/statistics/overview")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2025-01-01T00:00:00")
                        .param("to", "2026-11-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_RANGE_INVALID"));
    }

    @Test
    void statisticsExportReturnsCsvWithSameScope() throws Exception {
        String token = login("admin", "123456");
        String from = LocalDateTime.now().minusDays(30).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String to = LocalDateTime.now().plusDays(1).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        mockMvc.perform(get("/api/statistics/export")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("statistics-")))
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    org.junit.jupiter.api.Assertions.assertTrue(content.contains("统计口径版本,STAT-V1"));
                    org.junit.jupiter.api.Assertions.assertTrue(content.contains("社团活跃度排行"));
                    org.junit.jupiter.api.Assertions.assertTrue(content.contains("按日趋势"));
                });
    }

    private String login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = login.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.accessToken");
    }
}
