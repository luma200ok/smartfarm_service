package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이터 1틱 전역 상한(contract §4.11 사이클 2 리뷰 P2-4) 검증 — 농장당 상한만 있으면 농장
 * 수만큼 곱해져 무한정 커지는 문제를 막기 위해 전 농장 합산 상한을 별도 프로퍼티(별도 스프링
 * 컨텍스트)로 낮춰 재현한다. {@code max-rows-per-farm}은 넉넉히 둬(100) 농장당 상한이 아니라
 * 전역 상한이 먼저 걸리는 상황만 검증한다.
 */
@TestPropertySource(properties = {
        "sensor-simulator.max-rows-per-tick=6",
        "sensor-simulator.max-rows-per-farm=100"
})
@Transactional
class SensorSimulatorGlobalCapIntegrationTest extends FarmTestSupport {

    @Autowired
    private SensorSimulatorService sensorSimulatorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Test
    @DisplayName("tick: 전역 상한(테스트 설정 6행)을 먼저 채운 농장 이후로는 뒤 농장을 이번 틱에서 생성하지 않는다")
    void tickCapsGenerationAcrossFarmsByGlobalBudget() throws Exception {
        String ownerToken = signupAndLogin("sim-global1");
        long farmA = createFarm(ownerToken, "농장-전역A");
        long zoneA = createZone(ownerToken, farmA, "A동");
        List<SensorMetric> fourMetrics = List.of(
                SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2, SensorMetric.EC);
        long deviceA = createSensorDevice(ownerToken, farmA, zoneA, "센서A", fourMetrics); // 4행

        long farmB = createFarm(ownerToken, "농장-전역B");
        long zoneB = createZone(ownerToken, farmB, "B동");
        long deviceB = createSensorDevice(ownerToken, farmB, zoneB, "센서B", fourMetrics); // 4행 필요하나 잔여 예산 2행뿐

        sensorSimulatorService.tick();

        long rowsA = sensorReadingRepository.findAll().stream()
                .filter(r -> r.getDeviceId().equals(deviceA)).count();
        long rowsB = sensorReadingRepository.findAll().stream()
                .filter(r -> r.getDeviceId().equals(deviceB)).count();

        // 먼저 처리되는 농장(id 오름차순 — farmA)은 전역 예산 안에서 전부 생성되고, 그 다음 농장은
        // 남은 예산(2행)이 필요 행 수(4행)에 못 미쳐 이번 틱엔 전혀 생성되지 않는다(부분 생성 금지 —
        // generateForFarm이 device 단위로만 끊으므로 잔여 예산 부족 시 그 device는 통째로 skip).
        assertThat(rowsA).isEqualTo(4);
        assertThat(rowsB).isZero();
    }

    private long createSensorDevice(String ownerToken, long farmId, long zoneId, String name,
                                     List<SensorMetric> metrics) throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, DeviceKind.SENSOR,
                                null, null, null, null, null, metrics))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
