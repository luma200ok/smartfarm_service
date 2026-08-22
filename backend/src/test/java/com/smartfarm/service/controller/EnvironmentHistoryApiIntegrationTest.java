package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/farms/{farmId}/environment/history} 통합 테스트(contract §4.6, 이슈 #52) —
 * env_snapshots는 farmId 무관 전 농장 공용 단일 스트림이라(§4.6·EnvironmentService 주석), 클래스
 * 전체를 {@link Transactional}로 감싸 테스트 종료 시 롤백한다(공유 Testcontainers 싱글턴 위에서
 * 다른 테스트 클래스로 데이터가 새지 않도록 — env_snapshots는 farmId 등 자연 격리 키가 없는 유일한
 * 전역 테이블이라 다른 통합 테스트와 달리 이 격리가 필요하다).
 */
@Transactional
class EnvironmentHistoryApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private EnvSnapshotRepository envSnapshotRepository;

    private EnvSnapshot save(LocalDateTime capturedAt, Double outdoorTemp, Double indoorTemp) {
        return envSnapshotRepository.save(EnvSnapshot.builder()
                .capturedAt(capturedAt)
                .outdoorTemp(outdoorTemp)
                .outdoorHumidity(50.0)
                .indoorTemp(indoorTemp)
                .indoorHumidity(45.0)
                .controlled(true)
                .build());
    }

    @Test
    @DisplayName("range 미지정 시 기본값 24h로 동작하고, 24h는 다운샘플 없이 원본 그대로 반환된다")
    void defaultsTo24hRawPoints() throws Exception {
        String token = signupAndLogin("농부1");
        long farmId = createFarm(token, "이력 농장1");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        save(now.minusHours(1), 20.0, 22.0);
        save(now.minusHours(30), 99.0, 99.0); // 24h 범위 밖 — 제외돼야 함

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"))
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].outdoorTemp").value(20.0))
                .andExpect(jsonPath("$.points[0].indoorTemp").value(22.0));
    }

    @Test
    @DisplayName("range=24h 명시 조회도 동일하게 동작한다")
    void explicit24h() throws Exception {
        String token = signupAndLogin("농부2");
        long farmId = createFarm(token, "이력 농장2");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        save(now.minusHours(2), 21.0, 23.0);

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history?range=24h")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"))
                .andExpect(jsonPath("$.points.length()").value(1));
    }

    @Test
    @DisplayName("range=7d는 30분 버킷으로 평균 집계된다")
    void range7dAggregatesInto30MinuteBuckets() throws Exception {
        String token = signupAndLogin("농부3");
        long farmId = createFarm(token, "이력 농장3");
        // 자정(day boundary)은 1800초(30분) 그리드에 항상 정렬돼 버킷 경계 계산이 결정적이다.
        LocalDateTime bucketStart = LocalDateTime.now().minusDays(2).toLocalDate().atStartOfDay();
        save(bucketStart.plusMinutes(5), 20.0, 20.0);
        save(bucketStart.plusMinutes(15), 24.0, 24.0); // 같은 30분 버킷 — 평균 22.0

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history?range=7d")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.points[0].outdoorTemp").value(22.0))
                .andExpect(jsonPath("$.points[0].indoorTemp").value(22.0));
    }

    @Test
    @DisplayName("range=30d는 2시간 버킷으로 평균 집계된다")
    void range30dAggregatesInto2HourBuckets() throws Exception {
        String token = signupAndLogin("농부4");
        long farmId = createFarm(token, "이력 농장4");
        // 자정은 7200초(2시간) 그리드에도 정렬된다.
        LocalDateTime bucketStart = LocalDateTime.now().minusDays(10).toLocalDate().atStartOfDay();
        save(bucketStart.plusMinutes(30), 10.0, 10.0);
        save(bucketStart.plusHours(1), 30.0, 30.0); // 같은 2시간 버킷 — 평균 20.0

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history?range=30d")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("30d"))
                .andExpect(jsonPath("$.points[0].outdoorTemp").value(20.0))
                .andExpect(jsonPath("$.points[0].indoorTemp").value(20.0));
    }

    @Test
    @DisplayName("빈 구간은 점을 생략한다 — 데이터가 없으면 points가 빈 배열")
    void emptyRangeOmitsPoints() throws Exception {
        String token = signupAndLogin("농부5");
        long farmId = createFarm(token, "이력 농장5(데이터 없음)");

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history?range=24h")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    @DisplayName("알 수 없는 range 값은 400 C001을 반환한다")
    void unknownRangeReturnsC001() throws Exception {
        String token = signupAndLogin("농부6");
        long farmId = createFarm(token, "이력 농장6");

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history?range=1h")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 이력 조회 시 403 F002를 반환한다")
    void nonMemberCannotReadHistory() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/environment/history")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }
}
