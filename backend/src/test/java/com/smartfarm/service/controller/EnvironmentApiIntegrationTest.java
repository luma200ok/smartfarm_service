package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.EnvironmentApiTestSupport;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvironmentApiIntegrationTest extends EnvironmentApiTestSupport {

    // ── 정상 매핑 ──────────────────────────────────────────

    @Test
    @DisplayName("정상 조회 시 200, ai-server snake_case 응답이 camelCase로 매핑된다")
    void findTodayEnvironmentSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "환경 농장");
        enqueueOkEnvironmentResponse();

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-20T09:00:00"))
                .andExpect(jsonPath("$.outdoor.temp").value(28.5))
                .andExpect(jsonPath("$.outdoor.humidity").value(62.0))
                .andExpect(jsonPath("$.indoor.temp").value(24.1))
                .andExpect(jsonPath("$.indoor.humidity").value(55.3))
                .andExpect(jsonPath("$.indoor.controlled").value(true))
                .andExpect(jsonPath("$.devices.length()").value(2))
                .andExpect(jsonPath("$.devices[0].name").value("dehumidifier"))
                .andExpect(jsonPath("$.devices[0].on").value(false))
                .andExpect(jsonPath("$.devices[1].name").value("cooling_fan"))
                .andExpect(jsonPath("$.devices[1].on").value(true))
                .andExpect(jsonPath("$.alerts").isArray())
                .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    @Test
    @DisplayName("ai-server 부분 응답(필드 누락)도 200으로 그대로 매핑되고 alerts에 사유가 실린다")
    void findTodayEnvironmentPartialResponse() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "부분응답 농장");
        enqueuePartialEnvironmentResponse();

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.outdoor").doesNotExist())
                .andExpect(jsonPath("$.indoor.temp").value(24.1))
                .andExpect(jsonPath("$.devices").isArray())
                .andExpect(jsonPath("$.devices.length()").value(0))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0]").value("외기 데이터 조회 실패(KMA 응답 없음)"));
    }

    // ── 60s 캐시 ──────────────────────────────────────────

    @Test
    @DisplayName("60s 캐시 내 재호출은 ai-server를 다시 호출하지 않고 동일 값을 반환한다")
    void findTodayEnvironmentUsesCacheWithinTtl() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmA = createFarm(ownerToken, "캐시 농장 A");
        long farmB = createFarm(ownerToken, "캐시 농장 B");
        // 응답은 딱 1건만 큐잉 — 두 번째 조회가 ai-server를 다시 두드리면 큐가 비어 있어 실패한다.
        enqueueOkEnvironmentResponse();

        mockMvc.perform(get("/api/farms/" + farmA + "/environment/today")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outdoor.temp").value(28.5));

        // 캐시는 farmId 무관 전역 단일 캐시(contract) — 다른 농장으로 재조회해도 캐시를 탄다.
        mockMvc.perform(get("/api/farms/" + farmB + "/environment/today")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outdoor.temp").value(28.5));

        assertThat(AI_SERVER.getRequestCount()).isEqualTo(1);
    }

    // ── ai-server 장애 ────────────────────────────────────

    @Test
    @DisplayName("ai-server 5xx 응답 시(캐시 없음) 502 D003을 반환한다")
    void findTodayEnvironmentAiServer5xx() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "오류 농장");
        AI_SERVER.enqueue(new MockResponse().setResponseCode(500));

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("D003"));
    }

    @Test
    @DisplayName("ai-server 응답 없음(타임아웃) 시(캐시 없음) 502 D003을 반환한다")
    void findTodayEnvironmentAiServerTimeout() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "타임아웃 농장");
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("D003"));
    }

    // ── cross-tenant ─────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 미멤버가 환경 데이터 조회 시 403 F002를 반환한다")
    void findTodayEnvironmentAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/today")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("존재하지 않는 farmId는 403 F002를 반환한다(멤버십 없음 — 농장 존재 유추 채널 차단)")
    void findTodayEnvironmentNonExistentFarm() throws Exception {
        String token = signupAndLogin("농부");

        mockMvc.perform(get("/api/farms/999999999/environment/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }
}
