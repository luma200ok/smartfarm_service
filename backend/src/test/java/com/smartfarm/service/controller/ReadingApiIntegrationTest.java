package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 센서 측정값 조회 API 통합 테스트(contract §4.11, 이슈 #90) — series/latest/level-summary
 * 세 엔드포인트의 검증·스코프 소속·다운샘플 경계·구조 삭제 시 렌더링을 확인한다.
 * sensor_readings는 farmId(및 zone/rack/level) 스코프 쿼리라 EnvironmentHistoryApiIntegrationTest
 * 와 달리 클래스 전체 트랜잭션 롤백이 필요 없다(테스트마다 유니크 farmId로 자연 격리).
 */
class ReadingApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    private void save(long farmId, long deviceId, Long zoneId, Long rackId, Long rackLevelId,
                       SensorMetric metric, double value, LocalDateTime measuredAt) {
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).zoneId(zoneId).rackId(rackId).rackLevelId(rackLevelId)
                .metric(metric).value(value).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED)
                .build());
    }

    // ── series ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("series: 정상 조회 — scope=farm, 24h 원본 그대로 반환하고 simulated=true")
    void seriesHappyPath() throws Exception {
        String token = signupAndLogin("series농부1");
        long farmId = createFarm(token, "시리즈농장1");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, null, null, "센서1");
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        save(farmId, deviceId, zoneId, null, null, SensorMetric.TEMPERATURE, 22.0, now.minusHours(1));

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE")
                        .param("range", "24h")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"))
                .andExpect(jsonPath("$.scope").value("farm"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.series.length()").value(1))
                .andExpect(jsonPath("$.series[0].metric").value("TEMPERATURE"))
                .andExpect(jsonPath("$.series[0].unit").value("°C"))
                .andExpect(jsonPath("$.series[0].points[0].value").value(22.0));
    }

    @Test
    @DisplayName("series: metrics 4개 초과는 C001")
    void seriesTooManyMetricsReturnsC001() throws Exception {
        String token = signupAndLogin("series농부2");
        long farmId = createFarm(token, "시리즈농장2");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE,HUMIDITY,CO2,EC,PH")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("series: scope 형식 위반은 C001")
    void seriesInvalidScopeFormatReturnsC001() throws Exception {
        String token = signupAndLogin("series농부3");
        long farmId = createFarm(token, "시리즈농장3");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE")
                        .param("scope", "invalid-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("series: scope=zone:{타 농장 zoneId}는 404 R001(cross-tenant 소속 검증)")
    void seriesCrossTenantZoneScopeReturnsR001() throws Exception {
        String ownerToken = signupAndLogin("series농부4");
        long farmId = createFarm(ownerToken, "시리즈농장4");
        String otherOwnerToken = signupAndLogin("series농부4-other");
        long otherFarmId = createFarm(otherOwnerToken, "시리즈농장4-타농장");
        long otherZoneId = createZone(otherOwnerToken, otherFarmId, "타농장 A동");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE")
                        .param("scope", "zone:" + otherZoneId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("series: range=7d는 30분 버킷으로 device 평균 후 시간 평균 집계된다")
    void seriesRange7dAggregatesInto30MinuteBuckets() throws Exception {
        String token = signupAndLogin("series농부5");
        long farmId = createFarm(token, "시리즈농장5");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, null, null, "센서1");
        LocalDateTime bucketStart = LocalDateTime.now().minusDays(2).toLocalDate().atStartOfDay();
        save(farmId, deviceId, zoneId, null, null, SensorMetric.TEMPERATURE, 20.0, bucketStart.plusMinutes(5));
        save(farmId, deviceId, zoneId, null, null, SensorMetric.TEMPERATURE, 24.0, bucketStart.plusMinutes(15));

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE")
                        .param("range", "7d")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.series[0].points.length()").value(1))
                .andExpect(jsonPath("$.series[0].points[0].value").value(22.0));
    }

    @Test
    @DisplayName("series: 미멤버는 403 F002")
    void seriesNonMemberReturnsF002() throws Exception {
        String ownerToken = signupAndLogin("series농부6");
        String otherToken = signupAndLogin("series농부6-other");
        long farmId = createFarm(ownerToken, "시리즈농장6");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/series")
                        .param("metrics", "TEMPERATURE")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── latest ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("latest: 값이 있는 층은 값+상태, 값이 없는 층은 IDLE로 채워진다")
    void latestFillsIdleForLevelsWithoutData() throws Exception {
        String token = signupAndLogin("latest농부1");
        long farmId = createFarm(token, "레이턴트농장1");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);
        long level1 = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long level2 = levelIdOfByNo(token, farmId, zoneId, rackId, 2);
        long deviceId = createDevice(token, farmId, zoneId, rackId, level1, "센서1");
        save(farmId, deviceId, zoneId, rackId, level1, SensorMetric.TEMPERATURE, 22.0, LocalDateTime.now());
        // level2는 데이터 없음 — IDLE 기대

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/latest")
                        .param("metric", "TEMPERATURE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("TEMPERATURE"))
                .andExpect(jsonPath("$.unit").value("°C"))
                .andExpect(jsonPath("$.racks[0].levels[0].value").value(22.0))
                .andExpect(jsonPath("$.racks[0].levels[0].state").value("OK"))
                .andExpect(jsonPath("$.racks[0].levels[1].value").doesNotExist())
                .andExpect(jsonPath("$.racks[0].levels[1].state").value("IDLE"));
    }

    @Test
    @DisplayName("latest: soft delete된 랙은 응답에서 제외된다(활성 구조만 렌더)")
    void latestExcludesSoftDeletedRack() throws Exception {
        String token = signupAndLogin("latest농부2");
        long farmId = createFarm(token, "레이턴트농장2");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/latest")
                        .param("metric", "TEMPERATURE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.racks.length()").value(0));
    }

    @Test
    @DisplayName("latest: zoneId가 타 농장 소속이면 404 R001")
    void latestCrossTenantZoneIdReturnsR001() throws Exception {
        String ownerToken = signupAndLogin("latest농부3");
        long farmId = createFarm(ownerToken, "레이턴트농장3");
        String otherOwnerToken = signupAndLogin("latest농부3-other");
        long otherFarmId = createFarm(otherOwnerToken, "레이턴트농장3-타농장");
        long otherZoneId = createZone(otherOwnerToken, otherFarmId, "타농장 A동");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/latest")
                        .param("metric", "TEMPERATURE")
                        .param("zoneId", String.valueOf(otherZoneId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ── level-summary ───────────────────────────────────────────────────

    @Test
    @DisplayName("level-summary: rackId 생략 시 C001")
    void levelSummaryMissingRackIdReturnsC001() throws Exception {
        String token = signupAndLogin("levelsum농부1");
        long farmId = createFarm(token, "레벨섬농장1");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/level-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("level-summary: 타 농장 rackId는 404 R002")
    void levelSummaryCrossTenantRackIdReturnsR002() throws Exception {
        String ownerToken = signupAndLogin("levelsum농부2");
        long farmId = createFarm(ownerToken, "레벨섬농장2");
        String otherOwnerToken = signupAndLogin("levelsum농부2-other");
        long otherFarmId = createFarm(otherOwnerToken, "레벨섬농장2-타농장");
        long otherZoneId = createZone(otherOwnerToken, otherFarmId, "타농장 A동");
        long otherRackId = createRack(otherOwnerToken, otherFarmId, otherZoneId, "R1", 1);

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/level-summary")
                        .param("rackId", String.valueOf(otherRackId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    @DisplayName("level-summary: 데이터 있는 층은 평균+편차+상태, 없는 지표는 IDLE로 채워진 7종 그리드를 반환한다")
    void levelSummaryReturnsFullMetricGridWithIdleForMissingData() throws Exception {
        String token = signupAndLogin("levelsum농부3");
        long farmId = createFarm(token, "레벨섬농장3");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long deviceId = createDevice(token, farmId, zoneId, rackId, levelId, "센서1");
        save(farmId, deviceId, zoneId, rackId, levelId, SensorMetric.TEMPERATURE, 22.0, LocalDateTime.now());

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/level-summary")
                        .param("rackId", String.valueOf(rackId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rackId").value(rackId))
                .andExpect(jsonPath("$.levels[0].metrics.length()").value(SensorMetric.values().length))
                .andExpect(jsonPath("$.levels[0].metrics[?(@.metric=='TEMPERATURE')].average").value(22.0))
                .andExpect(jsonPath("$.levels[0].metrics[?(@.metric=='TEMPERATURE')].state").value("OK"))
                .andExpect(jsonPath("$.levels[0].metrics[?(@.metric=='CO2')].state").value("IDLE"));
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
        throw new IllegalStateException("층을 찾을 수 없음");
    }
}
