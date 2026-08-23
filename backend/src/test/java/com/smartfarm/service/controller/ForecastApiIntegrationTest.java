package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.KmaApiTestSupport;
import java.time.format.DateTimeFormatter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForecastApiIntegrationTest extends KmaApiTestSupport {

    private static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── 정상 매핑 ──────────────────────────────────────────

    @Test
    @DisplayName("정상 조회 시 200, KMA 응답이 카테고리별로 매핑된다")
    void findForecastSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "예보 농장");
        enqueueOkForecastResponse();
        String expectedTime = forecastPointTime().format(ISO_MINUTE);

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/forecast")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].time").value(expectedTime))
                .andExpect(jsonPath("$.points[0].temp").value(26.0))
                .andExpect(jsonPath("$.points[0].humidity").value(60.0))
                .andExpect(jsonPath("$.points[0].sky").value("SUNNY"))
                .andExpect(jsonPath("$.points[0].pop").value(10));
    }

    // ── 60분 캐시 ──────────────────────────────────────────

    @Test
    @DisplayName("60분 캐시 내 재호출은 KMA를 다시 호출하지 않고 동일 값을 반환한다")
    void findForecastUsesCacheWithinTtl() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmA = createFarm(ownerToken, "캐시 농장 A");
        long farmB = createFarm(ownerToken, "캐시 농장 B");
        int before = KMA_SERVER.getRequestCount();
        enqueueOkForecastResponse();

        mockMvc.perform(get("/api/farms/" + farmA + "/environment/forecast")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].temp").value(26.0));

        // 캐시는 farmId 무관 전역 단일 캐시(contract) — 다른 농장으로 재조회해도 캐시를 탄다.
        mockMvc.perform(get("/api/farms/" + farmB + "/environment/forecast")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].temp").value(26.0));

        assertThat(KMA_SERVER.getRequestCount() - before).isEqualTo(1);
    }

    // ── KMA 장애 ────────────────────────────────────────────

    @Test
    @DisplayName("KMA resultCode 오류(캐시 없음) 시 502 W001을 반환한다")
    void findForecastKmaResultCodeError() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "오류 농장");
        enqueueKmaErrorResultCode();

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/forecast")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("W001"));
    }

    @Test
    @DisplayName("KMA 응답 없음(타임아웃, 캐시 없음) 시 502 W001을 반환한다")
    void findForecastKmaTimeout() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "타임아웃 농장");
        KMA_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/forecast")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("W001"));
    }

    // ── cross-tenant ─────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 미멤버가 예보 조회 시 403 F002를 반환한다")
    void findForecastAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/forecast")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }
}
