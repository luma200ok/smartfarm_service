package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 장비 시뮬레이터 tick() 통합 테스트(contract §4.11, 이슈 #90) — application-test.yml에서
 * {@code sensor-simulator.max-rows-per-farm=5}로 낮춰 소수 장비로 상한 초과 케이스를 재현한다
 * (사이클 2 정정 — 상한은 센서 대수가 아니라 1틱 생성 행 수 기준).
 * sensor_readings는 farmId 자연 격리 키가 있지만, DeviceRepository#findByKindAndStatusNotOrderByIdAsc가
 * <b>전 농장</b> 대상이라 다른 테스트가 만든 장비까지 섞일 수 있어 클래스를 {@link Transactional}로
 * 감싸고 각 테스트가 자체 검증 범위를 그 테스트가 만든 farmId/deviceId로 좁힌다.
 */
@Transactional
class SensorSimulatorServiceIntegrationTest extends FarmTestSupport {

    @Autowired
    private SensorSimulatorService sensorSimulatorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Test
    @DisplayName("tick: kind=SENSOR·status!=OFFLINE 장비는 그 장비가 선언한 metrics만 생성한다(사이클 2 — 7종 일괄 생성 아님)")
    void tickGeneratesOnlyDeclaredMetrics() throws Exception {
        String ownerToken = signupAndLogin("sim-owner1");
        long farmId = createFarm(ownerToken, "농장-시뮬1");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 3);
        long levelId = levelIdOf(ownerToken, farmId, zoneId, rackId, 2);
        List<SensorMetric> declared = List.of(SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2);
        long deviceId = createSensorDevice(ownerToken, farmId, zoneId, rackId, levelId, "센서1", declared);

        sensorSimulatorService.tick();

        List<com.smartfarm.service.entity.SensorReading> readings =
                sensorReadingRepository.findAll().stream()
                        .filter(r -> r.getDeviceId().equals(deviceId))
                        .toList();

        assertThat(readings).hasSize(declared.size());
        assertThat(readings.stream().map(com.smartfarm.service.entity.SensorReading::getMetric).toList())
                .containsExactlyInAnyOrderElementsOf(declared);
        readings.forEach(r -> {
            assertThat(r.getFarmId()).isEqualTo(farmId);
            assertThat(r.getZoneId()).isEqualTo(zoneId);
            assertThat(r.getRackId()).isEqualTo(rackId);
            assertThat(r.getRackLevelId()).isEqualTo(levelId);
            assertThat(r.getSource()).isEqualTo(com.smartfarm.service.entity.SensorSource.SIMULATED);
        });
    }

    @Test
    @DisplayName("tick: kind=CONTROLLER 장비는 대상에서 제외된다")
    void tickExcludesNonSensorKind() throws Exception {
        String ownerToken = signupAndLogin("sim-owner2");
        long farmId = createFarm(ownerToken, "농장-시뮬2");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createControllerDevice(ownerToken, farmId, zoneId, "제어기1");

        sensorSimulatorService.tick();

        boolean anyReadingForController = sensorReadingRepository.findAll().stream()
                .anyMatch(r -> r.getDeviceId().equals(deviceId));
        assertThat(anyReadingForController).isFalse();
    }

    @Test
    @DisplayName("tick: status=OFFLINE 장비는 대상에서 제외된다")
    void tickExcludesOfflineDevices() throws Exception {
        String ownerToken = signupAndLogin("sim-owner3");
        long farmId = createFarm(ownerToken, "농장-시뮬3");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");
        setDeviceStatus(ownerToken, farmId, deviceId, DeviceStatus.OFFLINE);

        sensorSimulatorService.tick();

        boolean anyReadingForOffline = sensorReadingRepository.findAll().stream()
                .anyMatch(r -> r.getDeviceId().equals(deviceId));
        assertThat(anyReadingForOffline).isFalse();
    }

    @Test
    @DisplayName("tick: 농장당 1틱 최대 행 수(테스트 설정 5) 초과 시 초과분은 생성하지 않는다(사이클 2 — 행 수 기준, 조용히 자르지 않고 WARN만)")
    @DirtiesContext
    void tickCapsGenerationPerFarmByRowCount() throws Exception {
        String ownerToken = signupAndLogin("sim-owner4");
        long farmId = createFarm(ownerToken, "농장-시뮬4");
        long zoneId = createZone(ownerToken, farmId, "A동");
        // 장비당 2개 지표 선언 — id 오름차순 누적 2,4,6행. 상한(5행)을 넘는 3번째 장비부터 skip돼야
        // 한다(장비 개수 기준이면 3개 다 통과했을 것 — 사이클 2가 정정한 지점).
        List<SensorMetric> twoMetrics = List.of(SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY);
        List<Long> deviceIds = List.of(
                createSensorDevice(ownerToken, farmId, zoneId, null, null, "센서1", twoMetrics),
                createSensorDevice(ownerToken, farmId, zoneId, null, null, "센서2", twoMetrics),
                createSensorDevice(ownerToken, farmId, zoneId, null, null, "센서3", twoMetrics));

        sensorSimulatorService.tick();

        long devicesWithReadings = deviceIds.stream()
                .filter(id -> sensorReadingRepository.findAll().stream()
                        .anyMatch(r -> r.getDeviceId().equals(id)))
                .count();
        long totalRows = sensorReadingRepository.findAll().stream()
                .filter(r -> deviceIds.contains(r.getDeviceId()))
                .count();

        assertThat(devicesWithReadings).isEqualTo(2);
        assertThat(totalRows).isEqualTo(4);
    }

    private long levelIdOf(String ownerToken, long farmId, long zoneId, long rackId, int levelNo) throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
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
        throw new IllegalStateException("층을 찾을 수 없음: rackId=" + rackId + ", levelNo=" + levelNo);
    }

    /** metrics를 명시적으로 지정해 SENSOR 장비를 등록한다(FarmTestSupport#createDevice는 TEMPERATURE 1개 고정). */
    private long createSensorDevice(String ownerToken, long farmId, Long zoneId, Long rackId, Long rackLevelId,
                                     String name, List<SensorMetric> metrics) throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, rackId, rackLevelId, name, DeviceKind.SENSOR,
                                null, null, null, null, null, metrics))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long createControllerDevice(String ownerToken, long farmId, long zoneId, String name) throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, DeviceKind.CONTROLLER,
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private void setDeviceStatus(String ownerToken, long farmId, long deviceId, DeviceStatus newStatus)
            throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, null, null, null, null, newStatus, null, null, null))))
                .andExpect(status().isOk());
    }
}
