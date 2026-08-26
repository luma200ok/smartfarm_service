package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 홈 대시보드 농장 수 상한(이슈 #139 handoff 판단) 전용 — 실사용 규모(기본 50)로 51개 농장을
 * 만들어 검증하면 테스트가 느려지므로, 별도 컨텍스트에서 {@code dashboard.max-farms}를 낮게
 * 오버라이드해 절단 동작 자체를 검증한다({@code SensorSimulatorSchedulerConditionalTest}와 동일한
 * {@code @TestPropertySource} 패턴 — 공유 컨텍스트를 쓰는 다른 테스트에 영향 주지 않도록 이 상수
 * 오버라이드는 별도 클래스로 분리했다).
 */
@TestPropertySource(properties = "dashboard.max-farms=2")
class DashboardFarmCapIntegrationTest extends FarmTestSupport {

    @Test
    @DisplayName("농장이 상한을 초과하면 500 없이 상한 개수로 잘려 반환된다")
    void dashboardTruncatesToConfiguredCapWithoutError() throws Exception {
        String token = signupAndLogin("상한테스트농부");
        createFarm(token, "농장A");
        createFarm(token, "농장B");
        createFarm(token, "농장C");

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
