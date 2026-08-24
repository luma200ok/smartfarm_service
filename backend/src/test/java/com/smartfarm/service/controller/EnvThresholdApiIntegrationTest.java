package com.smartfarm.service.controller;

import com.smartfarm.service.entity.FarmRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code GET/PUT /api/farms/{farmId}/env-thresholds} 통합 테스트(contract §4.6, 이슈 #52).
 */
class EnvThresholdApiIntegrationTest extends FarmTestSupport {

    // --- 데모 계정 헬퍼(DemoAccountApiIntegrationTest와 동일 패턴 — 이 테스트는 FarmTestSupport를
    // 상속하고 있어 그 클래스의 private 헬퍼를 재사용할 수 없다) ---

    private String demoAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private long demoFarmId(String demoToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode farms = readJson(result);
        assertThat(farms.size()).isGreaterThanOrEqualTo(1);
        return farms.get(0).get("id").asLong();
    }

    @Test
    @DisplayName("미설정 농장은 GET 시 enabled=false 기본값을 반환한다(404 아님)")
    void findReturnsDefaultWhenNotConfigured() throws Exception {
        String token = signupAndLogin("주인장1");
        long farmId = createFarm(token, "임계치 농장1");

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.indoorTempMin").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("OWNER는 임계치를 설정할 수 있고 GET에 반영된다")
    void ownerCanSetThresholds() throws Exception {
        String token = signupAndLogin("주인장2");
        long farmId = createFarm(token, "임계치 농장2");

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"indoorTempMin":18.0,"indoorTempMax":28.0,
                                 "indoorHumidityMin":40.0,"indoorHumidityMax":80.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.indoorTempMin").value(18.0))
                .andExpect(jsonPath("$.indoorTempMax").value(28.0));

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.indoorHumidityMax").value(80.0));
    }

    @Test
    @DisplayName("min >= max는 400 C001을 반환한다(교차 검증)")
    void rejectsInvertedRange() throws Exception {
        String token = signupAndLogin("주인장3");
        long farmId = createFarm(token, "임계치 농장3");

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMin\":30.0,\"indoorTempMax\":20.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("범위를 벗어난 값(온도 -50~80)은 400 C001을 반환한다")
    void rejectsOutOfRangeValue() throws Exception {
        String token = signupAndLogin("주인장4");
        long farmId = createFarm(token, "임계치 농장4");

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMin\":-60.0,\"indoorTempMax\":20.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("min/max 한쪽만 설정해도 유효하다(그 방향만 감시)")
    void singleBoundIsValid() throws Exception {
        String token = signupAndLogin("주인장5");
        long farmId = createFarm(token, "임계치 농장5");

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMax\":30.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indoorTempMin").doesNotExist())
                .andExpect(jsonPath("$.indoorTempMax").value(30.0));
    }

    @Test
    @DisplayName("MEMBER가 설정 시도 시 403 F003을 반환한다")
    void memberCannotUpdateThresholds() throws Exception {
        String ownerToken = signupAndLogin("주인장6");
        String memberToken = signupAndLogin("일꾼이6");
        long farmId = createFarm(ownerToken, "임계치 농장6");
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMin\":18.0,\"indoorTempMax\":28.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("MEMBER는 조회는 가능하다")
    void memberCanReadThresholds() throws Exception {
        String ownerToken = signupAndLogin("주인장7");
        String memberToken = signupAndLogin("일꾼이7");
        long farmId = createFarm(ownerToken, "임계치 농장7");
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 조회/수정 시 403 F002를 반환한다")
    void nonMemberCannotAccessThresholds() throws Exception {
        String ownerToken = signupAndLogin("주인장8");
        String otherToken = signupAndLogin("남남남8");
        long farmId = createFarm(ownerToken, "격리 농장8");

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMin\":18.0,\"indoorTempMax\":28.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("데모 계정은 OWNER여도 임계치 설정(PUT)이 403 A007로 차단된다(contract §4.5 갱신, 리뷰 P2) — GET 조회는 허용")
    void demoAccountCannotUpdateThresholdsButCanRead() throws Exception {
        String demoToken = demoAccessToken();
        long farmId = demoFarmId(demoToken);

        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"indoorTempMin\":18.0,\"indoorTempMax\":28.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk());
    }
}
