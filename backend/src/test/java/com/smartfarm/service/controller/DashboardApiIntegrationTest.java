package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.SensorReadingRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 홈 대시보드 농장 카드 API 통합 테스트(이슈 #139) — {@code GET /api/dashboard/farms}.
 * {@code FarmApiIntegrationTest}(cross-tenant 원칙)·{@code AlarmEventApiIntegrationTest}(알람
 * 픽스처 패턴)와 동일한 실 DB(Testcontainers PostgreSQL) 검증 방식을 따른다. 이 이슈의 핵심인
 * N+1 부재는 Hibernate Statistics로 쿼리 "개수"가 농장 수와 무관하게 상수인지 직접 잰다
 * (mock으로 호출 여부만 확인하면 배치 조회를 가장한 채 내부에서 루프를 돌려도 통과하므로 부족함).
 */
class DashboardApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Autowired
    private RackLevelRepository rackLevelRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private void saveReading(long farmId, long deviceId, Long rackLevelId, SensorMetric metric,
                              double value, LocalDateTime measuredAt) {
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackLevelId(rackLevelId)
                .metric(metric).value(value).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());
    }

    private AlarmEvent saveAlarm(long farmId, AlarmSeverity severity, String metricKey, String message) {
        return alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(severity)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(metricKey)
                .message(message)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /** 랙 1개(층 3개)·지표 3종 최신값·CRITICAL 미확인 알람 1건을 갖춘 "정상 가동 중" 농장 픽스처. */
    private long seedFullyPopulatedFarm(String token, String farmName) throws Exception {
        long farmId = createFarm(token, farmName);
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 3);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long deviceId = createDevice(token, farmId, null, null, levelId, "센서1");

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        saveReading(farmId, deviceId, levelId, SensorMetric.TEMPERATURE, 22.0, now);
        saveReading(farmId, deviceId, levelId, SensorMetric.HUMIDITY, 60.0, now);
        saveReading(farmId, deviceId, levelId, SensorMetric.EC, 10.0, now); // 임계 상한(4.0) 크게 초과 → outOfRange
        saveAlarm(farmId, AlarmSeverity.CRITICAL, "TEMP_HIGH", "B3랙 4층 급액 EC 초과");
        return farmId;
    }

    private long levelIdOfByNo(String token, long farmId, long zoneId, long rackId, int levelNo) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = readJson(result);
        for (JsonNode zone : json.get("zones")) {
            if (zone.get("id").asLong() != zoneId) {
                continue;
            }
            for (JsonNode rack : zone.get("racks")) {
                if (rack.get("id").asLong() != rackId) {
                    continue;
                }
                for (JsonNode level : rack.get("levels")) {
                    if (level.get("levelNo").asInt() == levelNo) {
                        return level.get("id").asLong();
                    }
                }
            }
        }
        throw new IllegalStateException("층을 찾을 수 없음: levelNo=" + levelNo);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    // ── 정상 카드 조립 ──────────────────────────────────────────────

    @Test
    @DisplayName("랙/층 수·지표 3열·상태 배지·미확인 알람 수·최근 알람 메시지·7일 추이를 한 번에 반환한다")
    void dashboardAssemblesCardFromBatchedData() throws Exception {
        String token = signupAndLogin("대시보드농부");
        long farmId = seedFullyPopulatedFarm(token, "군산 제1식물공장");

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.farms.length()").value(1))
                .andExpect(jsonPath("$.farms[0].id").value(farmId))
                .andExpect(jsonPath("$.farms[0].name").value("군산 제1식물공장"))
                .andExpect(jsonPath("$.farms[0].rackCount").value(1))
                .andExpect(jsonPath("$.farms[0].levelCount").value(3))
                .andExpect(jsonPath("$.farms[0].status").value("CRITICAL"))
                .andExpect(jsonPath("$.farms[0].unacknowledgedAlarmCount").value(1))
                .andExpect(jsonPath("$.farms[0].latestAlarmMessage").value("B3랙 4층 급액 EC 초과"))
                .andExpect(jsonPath("$.farms[0].metrics.length()").value(3))
                .andExpect(jsonPath("$.farms[0].metrics[?(@.metric=='TEMPERATURE')].value").value(22.0))
                .andExpect(jsonPath("$.farms[0].metrics[?(@.metric=='TEMPERATURE')].unit").value("°C"))
                .andExpect(jsonPath("$.farms[0].metrics[?(@.metric=='TEMPERATURE')].outOfRange").value(false))
                .andExpect(jsonPath("$.farms[0].metrics[?(@.metric=='EC')].value").value(10.0))
                .andExpect(jsonPath("$.farms[0].metrics[?(@.metric=='EC')].outOfRange").value(true))
                .andExpect(jsonPath("$.farms[0].trend7d.length()").value(7))
                .andExpect(jsonPath("$.farms[0].trend7d[6].value").value(22.0))
                .andExpect(jsonPath("$.farms[0].trend7d[6].state").value("OK"))
                // 없는 값을 만들지 않는다(#130 부재) — plantedDaysAgo 필드 자체가 응답에 없어야 한다.
                .andExpect(jsonPath("$.farms[0].plantedDaysAgo").doesNotExist());
    }

    @Test
    @DisplayName("구조·측정값·알람이 하나도 없는 농장은 0/빈 값으로 안전하게 내려간다")
    void dashboardHandlesFarmWithNoDataGracefully() throws Exception {
        String token = signupAndLogin("빈농장주");
        long farmId = createFarm(token, "정읍 R&D 센터");

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.farms.length()").value(1))
                .andExpect(jsonPath("$.farms[0].id").value(farmId))
                .andExpect(jsonPath("$.farms[0].rackCount").value(0))
                .andExpect(jsonPath("$.farms[0].levelCount").value(0))
                .andExpect(jsonPath("$.farms[0].status").value("NORMAL"))
                .andExpect(jsonPath("$.farms[0].unacknowledgedAlarmCount").value(0))
                .andExpect(jsonPath("$.farms[0].latestAlarmMessage").doesNotExist())
                .andExpect(jsonPath("$.farms[0].metrics.length()").value(3))
                .andExpect(jsonPath("$.farms[0].metrics[0].value").doesNotExist())
                .andExpect(jsonPath("$.farms[0].metrics[0].outOfRange").value(false))
                .andExpect(jsonPath("$.farms[0].trend7d.length()").value(7))
                .andExpect(jsonPath("$.farms[0].trend7d[6].value").doesNotExist())
                .andExpect(jsonPath("$.farms[0].trend7d[6].state").value("IDLE"));
    }

    @Test
    @DisplayName("층이 soft delete되면 그 층의 잔존 측정값이 카드 지표 '최신값'으로 노출되지 않는다"
            + "(리뷰 P1 — findLatestByFarmIdsAndMetrics soft-delete 필터 누락)")
    void dashboardExcludesSoftDeletedLevelReadingsFromLatestMetrics() throws Exception {
        String token = signupAndLogin("층삭제농부");
        long farmId = createFarm(token, "층삭제 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long deviceId = createDevice(token, farmId, null, null, levelId, "센서1");

        // 이 농장의 유일한 측정값은 이 층에만 있다 — 층이 soft delete되면 그 이력도 함께 제외돼야
        // "최신값"이 null(측정 이력 없음)로 떨어진다(값이 있는데 숨는 게 아니라, 애초에 값이 없는
        // 상태가 정답이다).
        saveReading(farmId, deviceId, levelId, SensorMetric.TEMPERATURE, 22.0, LocalDateTime.now());

        rackLevelRepository.deleteById(levelId); // @SQLDelete → soft delete(deleted_at 설정)

        // CARD_METRICS 고정 순서(TEMPERATURE·HUMIDITY·EC)라 index 0 = TEMPERATURE — 필터
        // jsonPath(indefinite path)는 값이 null이어도 "원소 1개짜리 리스트"로 남아 doesNotExist()가
        // 통과하지 못하므로, 정의된(definite) index 경로로 직접 확인한다.
        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms[0].metrics[0].metric").value("TEMPERATURE"))
                .andExpect(jsonPath("$.farms[0].metrics[0].value").doesNotExist())
                .andExpect(jsonPath("$.farms[0].metrics[0].outOfRange").value(false));
    }

    // ── 스코프(타 사용자·PENDING) ──────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 내 대시보드에는 타 사용자 농장이 한 건도 섞이지 않는다")
    void dashboardExcludesOtherUsersFarms() throws Exception {
        String tokenA = signupAndLogin("갑농부-대시보드");
        String tokenB = signupAndLogin("을농부-대시보드");
        long farmA = createFarm(tokenA, "갑 농장");
        createFarm(tokenB, "을 농장");

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms.length()").value(1))
                .andExpect(jsonPath("$.farms[0].id").value(farmA));
    }

    @Test
    @DisplayName("PENDING(승인 대기) 멤버십 농장은 /api/farms에는 보이지만 대시보드에서는 제외된다")
    void dashboardExcludesPendingMembershipFarm() throws Exception {
        String adminToken = signupAndLogin("승인대기-관리자");
        String joinerToken = signupAndLogin("승인대기-신청자");
        long farmId = createFarm(adminToken, "합류 대기 농장");
        acceptInvitation(joinerToken, createInvitationCode(adminToken, farmId)); // 승인 전 PENDING

        // /api/farms는 PENDING도 포함해 FE가 "대기 중" 뱃지를 그린다(이슈 #122 선례) — 대시보드와 대비.
        mockMvc.perform(get("/api/farms").header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myRole").value("PENDING"));

        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    @DisplayName("무인증으로 대시보드 조회 시 401을 반환한다")
    void dashboardWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/farms"))
                .andExpect(status().isUnauthorized());
    }

    // ── N+1 부재(이 이슈의 핵심) ─────────────────────────────────────

    @Test
    @DisplayName("농장 수가 3배로 늘어도 실행되는 SQL 쿼리 개수는 그대로다 (N+1 없음)")
    void queryCountStaysConstantAsFarmCountGrows() throws Exception {
        String token = signupAndLogin("쿼리카운트농부");
        seedFullyPopulatedFarm(token, "농장1");

        statistics().clear();
        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms.length()").value(1));
        long queryCountFor1Farm = statistics().getPrepareStatementCount();

        seedFullyPopulatedFarm(token, "농장2");
        seedFullyPopulatedFarm(token, "농장3");

        statistics().clear();
        mockMvc.perform(get("/api/dashboard/farms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms.length()").value(3));
        long queryCountFor3Farms = statistics().getPrepareStatementCount();

        assertThat(queryCountFor3Farms)
                .as("농장 1개 대비 3개일 때 실행 SQL 개수(배치 조회라 farm 수와 무관해야 함)")
                .isEqualTo(queryCountFor1Farm);
    }
}
