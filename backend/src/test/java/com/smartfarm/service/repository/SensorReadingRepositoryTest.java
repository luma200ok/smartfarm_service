package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SensorReadingRepository} 네이티브 집계 쿼리 검증(contract §4.11, 이슈 #90) — 특히
 * scope 필터(zoneId/rackId/rackLevelId)의 nullable 파라미터 바인딩(CAST(:x AS BIGINT) IS NULL
 * 패턴)이 실제 Postgres에서 의도대로 동작하는지가 핵심.
 */
@Transactional
class SensorReadingRepositoryTest extends FarmTestSupport {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Test
    @DisplayName("findSeriesAggregated: scope 필터 전부 null(farm 스코프)이면 farm 내 전 데이터를 집계한다")
    void findSeriesAggregatedWithNullScopeFiltersAggregatesAllFarmData() throws Exception {
        String ownerToken = signupAndLogin("repo-owner1");
        long farmId = createFarm(ownerToken, "농장R1");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).zoneId(zoneId)
                .metric(SensorMetric.TEMPERATURE).value(20.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        List<ReadingSeriesBucketProjection> result = sensorReadingRepository.findSeriesAggregated(
                farmId, SensorMetric.TEMPERATURE.name(), measuredAt.minusHours(1), 60L, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("findSeriesAggregated: zoneId 필터를 주면 다른 존 데이터는 제외된다")
    void findSeriesAggregatedFiltersByZoneId() throws Exception {
        String ownerToken = signupAndLogin("repo-owner2");
        long farmId = createFarm(ownerToken, "농장R2");
        long zoneA = createZone(ownerToken, farmId, "A동");
        long zoneB = createZone(ownerToken, farmId, "B동");
        long deviceA = createDevice(ownerToken, farmId, zoneA, null, null, "센서A");
        long deviceB = createDevice(ownerToken, farmId, zoneB, null, null, "센서B");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceA).zoneId(zoneA)
                .metric(SensorMetric.TEMPERATURE).value(20.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceB).zoneId(zoneB)
                .metric(SensorMetric.TEMPERATURE).value(99.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        List<ReadingSeriesBucketProjection> result = sensorReadingRepository.findSeriesAggregated(
                farmId, SensorMetric.TEMPERATURE.name(), measuredAt.minusHours(1), 60L, zoneA, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("findSeriesAggregated: 같은 층 여러 device는 device 간 평균으로 1개 값이 된다")
    void findSeriesAggregatedAveragesAcrossDevicesInSameLevel() throws Exception {
        String ownerToken = signupAndLogin("repo-owner3");
        long farmId = createFarm(ownerToken, "농장R3");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        long levelId = levelIdOf(ownerToken, farmId, zoneId, rackId);
        long device1 = createDevice(ownerToken, farmId, null, null, levelId, "센서1");
        long device2 = createDevice(ownerToken, farmId, null, null, levelId, "센서2");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(device1).rackLevelId(levelId)
                .metric(SensorMetric.TEMPERATURE).value(10.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(device2).rackLevelId(levelId)
                .metric(SensorMetric.TEMPERATURE).value(30.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        List<ReadingSeriesBucketProjection> result = sensorReadingRepository.findSeriesAggregated(
                farmId, SensorMetric.TEMPERATURE.name(), measuredAt.minusHours(1), 60L, null, null, levelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(20.0); // (10+30)/2
    }

    @Test
    @DisplayName("집계 순서: 센서 대수가 많은 층이 가중치를 더 갖지 않는다(층별 선 평균 → 층 간 평균)")
    void aggregationOrderPreventsSensorCountBiasAcrossLevels() throws Exception {
        String ownerToken = signupAndLogin("repo-owner4");
        long farmId = createFarm(ownerToken, "농장R4");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 2);
        long level1 = levelIdOf(ownerToken, farmId, zoneId, rackId);
        long level2 = levelIdOfByNo(ownerToken, farmId, zoneId, rackId, 2);

        // level1: 센서 3대(전부 0.0) — 대수가 많음. level2: 센서 1대(100.0).
        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        for (int i = 0; i < 3; i++) {
            long deviceId = createDevice(ownerToken, farmId, null, rackId, level1, "센서L1-" + i);
            sensorReadingRepository.save(SensorReading.builder()
                    .farmId(farmId).deviceId(deviceId).rackId(rackId).rackLevelId(level1)
                    .metric(SensorMetric.TEMPERATURE).value(0.0).measuredAt(measuredAt)
                    .source(SensorSource.SIMULATED).build());
        }
        long deviceL2 = createDevice(ownerToken, farmId, null, rackId, level2, "센서L2");
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceL2).rackId(rackId).rackLevelId(level2)
                .metric(SensorMetric.TEMPERATURE).value(100.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        // scope=rack(두 층 다 포함) — 만약 device 평탄 평균이면 (0+0+0+100)/4=25, 층별 선 평균 후
        // 층 간 평균이면 (0+100)/2=50이어야 한다(핸드오프 요건 5).
        List<ReadingSeriesBucketProjection> result = sensorReadingRepository.findSeriesAggregated(
                farmId, SensorMetric.TEMPERATURE.name(), measuredAt.minusHours(1), 60L, null, rackId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual: SIMULATED 데이터 존재 확인")
    void existsSimulatedInFarmScope() throws Exception {
        String ownerToken = signupAndLogin("repo-owner5");
        long farmId = createFarm(ownerToken, "농장R5");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");
        LocalDateTime measuredAt = LocalDateTime.now();
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).zoneId(zoneId)
                .metric(SensorMetric.HUMIDITY).value(50.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        boolean exists = sensorReadingRepository.existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                farmId, SensorMetric.HUMIDITY, SensorSource.SIMULATED, measuredAt.minusHours(1));
        boolean notExists = sensorReadingRepository.existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                farmId, SensorMetric.CO2, SensorSource.SIMULATED, measuredAt.minusHours(1));

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("findSeriesAggregated: 랙 삭제(soft delete)로 층이 사라지면 farm 스코프 집계에서도 제외된다 "
            + "(사이클 2 리뷰 P2-3 — scope=farm이 soft delete된 구조의 과거 이력을 계속 포함하던 버그)")
    void findSeriesAggregatedExcludesSoftDeletedRackLevelInFarmScope() throws Exception {
        String ownerToken = signupAndLogin("repo-owner8");
        long farmId = createFarm(ownerToken, "농장R8");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        long levelId = levelIdOf(ownerToken, farmId, zoneId, rackId);
        long deviceId = createDevice(ownerToken, farmId, null, null, levelId, "센서1");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackId(rackId).rackLevelId(levelId)
                .metric(SensorMetric.TEMPERATURE).value(99.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        // 랙(및 하위 층) soft delete — RackService#ensureNoActiveDevices 때문에 장비를 먼저 지워야 한다.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());

        List<ReadingSeriesBucketProjection> result = sensorReadingRepository.findSeriesAggregated(
                farmId, SensorMetric.TEMPERATURE.name(), measuredAt.minusHours(1), 60L, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findLatestPerLevel: 같은 device·같은 measuredAt에 다른 지표가 있어도 요청 metric 값만 반환한다 "
            + "(사이클 2 리뷰 P1-1 회귀 — 바깥 SELECT의 farm_id/metric 술어 누락 시 전 지표 평균으로 오염됨)")
    void findLatestPerLevelDoesNotMixOtherMetricsAtSameTick() throws Exception {
        String ownerToken = signupAndLogin("repo-owner6");
        long farmId = createFarm(ownerToken, "농장R6");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        long levelId = levelIdOf(ownerToken, farmId, zoneId, rackId);
        long deviceId = createDevice(ownerToken, farmId, null, null, levelId, "온습도센서");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackLevelId(levelId)
                .metric(SensorMetric.TEMPERATURE).value(22.4).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackLevelId(levelId)
                .metric(SensorMetric.CO2).value(650.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        List<ReadingLevelLatestProjection> result = sensorReadingRepository.findLatestPerLevel(
                farmId, SensorMetric.TEMPERATURE.name(), List.of(levelId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo(22.4); // CO2(650.0)와 섞이면 336.2가 됨
    }

    @Test
    @DisplayName("findLevelSummaryAggregated: metric별로 GROUP BY가 이미 걸려있어 같은 틱의 다른 지표와 섞이지 않는다 "
            + "(findLatestPerLevel의 P1-1과 동일 패턴이 없는지 확인하는 회귀 테스트)")
    void findLevelSummaryAggregatedDoesNotMixOtherMetricsAtSameTick() throws Exception {
        String ownerToken = signupAndLogin("repo-owner7");
        long farmId = createFarm(ownerToken, "농장R7");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        long levelId = levelIdOf(ownerToken, farmId, zoneId, rackId);
        long deviceId = createDevice(ownerToken, farmId, null, null, levelId, "온습도센서");

        LocalDateTime measuredAt = LocalDateTime.now().withSecond(0).withNano(0);
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackId(rackId).rackLevelId(levelId)
                .metric(SensorMetric.TEMPERATURE).value(22.4).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).rackId(rackId).rackLevelId(levelId)
                .metric(SensorMetric.CO2).value(650.0).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED).build());

        List<ReadingLevelAverageProjection> result = sensorReadingRepository.findLevelSummaryAggregated(
                farmId, rackId, measuredAt.minusHours(1));

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(row -> {
            if (row.getMetric().equals(SensorMetric.TEMPERATURE.name())) {
                assertThat(row.getAverage()).isEqualTo(22.4);
            }
        });
        assertThat(result).anySatisfy(row -> {
            if (row.getMetric().equals(SensorMetric.CO2.name())) {
                assertThat(row.getAverage()).isEqualTo(650.0);
            }
        });
    }

    private long levelIdOf(String ownerToken, long farmId, long zoneId, long rackId) throws Exception {
        return levelIdOfByNo(ownerToken, farmId, zoneId, rackId, 1);
    }

    private long levelIdOfByNo(String ownerToken, long farmId, long zoneId, long rackId, int levelNo)
            throws Exception {
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        var json = readJson(result);
        for (var zone : json.get("zones")) {
            if (zone.get("id").asLong() != zoneId) {
                continue;
            }
            for (var rack : zone.get("racks")) {
                if (rack.get("id").asLong() != rackId) {
                    continue;
                }
                for (var level : rack.get("levels")) {
                    if (level.get("levelNo").asInt() == levelNo) {
                        return level.get("id").asLong();
                    }
                }
            }
        }
        throw new IllegalStateException("층을 찾을 수 없음");
    }
}
