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
    @DisplayName("농장이 상한을 초과하면 500 없이 상한 개수로 잘리고, totalCount(절단 전 개수)·"
            + "truncated=true로 절단 사실이 응답에 명시된다(이슈 #140) — "
            + "잘리고 남는 농장이 어느 것인지(생성 순서=id 오름차순, "
            + "FarmMemberRepository#findMyFarms의 ORDER BY f.id ASC)까지 확인한다(리뷰 P2 — "
            + "개수만 보면 정렬이 깨져 엉뚱한 농장이 잘려도 통과한다)")
    void dashboardTruncatesToConfiguredCapWithoutError() throws Exception {
        String token = signupAndLogin("상한테스트농부");
        createFarm(token, "농장A");
        createFarm(token, "농장B");
        createFarm(token, "농장C");

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.truncated").value(true))
                // 절단 순서는 findMyFarms의 f.id ASC로 결정적이다 — 먼저 생성한 농장A·농장B가
                // 남고, 상한을 초과해 나중에 생성된 농장C가 잘려나가야 한다.
                .andExpect(jsonPath("$.farms[0].name").value("농장A"))
                .andExpect(jsonPath("$.farms[1].name").value("농장B"));
    }
}
