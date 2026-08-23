package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제어 ↔ 시뮬레이터 접점 검증(contract §4.12 "시뮬레이터 연동") — 적용된 목표값이 <b>실제 측정값에
 * 반영</b>돼야 데모가 성립한다. 검증 항목: ① 목표값이 기저를 대체하고 tick당 일정 비율로 수렴 ②
 * 꺼진 제어기가 있는 존은 수렴하지 않고 자연 표류 ③ OFF 센서는 아예 생성 대상에서 빠진다
 * ④ 어느 경로든 {@code source}는 {@code SIMULATED}.
 *
 * <p>1틱 상한을 크게 열어둔 별도 컨텍스트를 쓴다 — 시뮬레이터의 대상 조회가 <b>전 농장</b>이라
 * 공유 컨테이너에 남은 다른 테스트의 장비가 기본 테스트 상한(전역 100행)을 먼저 소진하면 이 테스트의
 * 농장이 순서 의존적으로 skip될 수 있다(§4.11 사이클 2에서 같은 이유로 단위 테스트를 분리했다).
 */
@Transactional
@TestPropertySource(properties = {
        "sensor-simulator.max-rows-per-tick=100000",
        "sensor-simulator.max-rows-per-farm=1000"
})
class SensorSimulatorControlIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private FarmService farmService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private ControlService controlService;

    @Autowired
    private SensorSimulatorService sensorSimulatorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    @DisplayName("적용된 목표값이 시뮬레이터 기저를 대체하고 직전 값에서 tick당 일정 비율로 수렴한다")
    void appliedSetpointConvergesFromPreviousValue() {
        Fixture fixture = fixture("수렴");
        long sensorId = createSensor(fixture, "온도센서");
        seedPreviousReading(fixture, sensorId, 10.0);
        applySetpoint(fixture, SensorMetric.TEMPERATURE, 30.0);

        sensorSimulatorService.tick();

        SensorReading reading = latestReading(sensorId);
        double expected = SensorSimulationProfile.converge(
                SensorMetric.TEMPERATURE, 10.0, 30.0, sensorId, reading.getMeasuredAt());
        assertThat(reading.getValue()).isEqualTo(expected, within(1e-9));
        // 즉시 점프가 아니라 한 걸음(직전 10 → 목표 30 사이의 14 근방)이다 — 그래프가 계단이 되지 않는다.
        assertThat(reading.getValue()).isBetween(13.0, 15.0);
        // 제어가 붙어도 실측이 되는 게 아니다(contract §4.12).
        assertThat(reading.getSource()).isEqualTo(SensorSource.SIMULATED);
    }

    @Test
    @DisplayName("꺼진 제어기가 있는 존은 목표로 수렴하지 않고, 자연값 쪽으로 같은 비율로 표류한다(계단 없음)")
    void zoneWithOffControllerDriftsBackToNaturalWithoutStep() {
        Fixture fixture = fixture("자연표류");
        long sensorId = createSensor(fixture, "온도센서");
        long controllerId = createController(fixture, "순환팬");
        seedPreviousReading(fixture, sensorId, 10.0);
        applySetpoint(fixture, SensorMetric.TEMPERATURE, 30.0);
        turnOffDevice(fixture, controllerId);

        sensorSimulatorService.tick();

        SensorReading reading = latestReading(sensorId);
        double natural = SensorSimulationProfile.simulate(
                SensorMetric.TEMPERATURE, 1, sensorId, reading.getMeasuredAt());
        double expected = SensorSimulationProfile.converge(
                SensorMetric.TEMPERATURE, 10.0, natural, sensorId, reading.getMeasuredAt());
        assertThat(reading.getValue()).isEqualTo(expected, within(1e-9));
        // 목표(30)로 가지도, 자연값(≈22)으로 점프하지도 않는다 — 직전 10에서 자연값 쪽 한 걸음.
        assertThat(reading.getValue()).isLessThan(natural);
        assertThat(reading.getValue()).isGreaterThan(10.0);
    }

    @Test
    @DisplayName("비상 정지 후에도 센서는 계속 측정한다 — 제어기만 OFF, 측정 스트림은 살아 있다(리뷰 P1)")
    void sensorsKeepMeasuringAfterEmergencyStop() {
        Fixture fixture = fixture("정지후측정");
        long sensorId = createSensor(fixture, "온도센서");
        long controllerId = createController(fixture, "순환팬");

        controlService.emergencyStop(fixture.farmId(), fixture.ownerId());
        sensorSimulatorService.tick();

        // 제어기는 꺼졌지만 센서는 NORMAL 그대로이고 측정값이 계속 생성된다.
        assertThat(deviceRepository.findById(controllerId).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.OFF);
        assertThat(deviceRepository.findById(sensorId).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.NORMAL);
        assertThat(sensorReadingRepository.findAll().stream()
                .anyMatch(reading -> reading.getDeviceId().equals(sensorId)))
                .as("비상 정지가 센서까지 끄면 농장 전체 측정이 영구 정지한다(복구 경로도 없다)")
                .isTrue();
    }

    @Test
    @DisplayName("제어로 꺼진(OFF) 센서는 시뮬레이터 대상에서 빠진다 — 껐는데 데이터가 흐르지 않는다")
    void offSensorProducesNoReadings() {
        Fixture fixture = fixture("센서끄기");
        long sensorId = createSensor(fixture, "온도센서");
        // OFF는 레지스트리 PATCH로 지정할 수 없다(§4.10 사이클 3) — 반드시 제어 경로를 거친다.
        turnOffDevice(fixture, sensorId);

        sensorSimulatorService.tick();

        assertThat(sensorReadingRepository.findAll().stream()
                .anyMatch(reading -> reading.getDeviceId().equals(sensorId))).isFalse();
    }

    @Test
    @DisplayName("목표값이 없는 존은 기존 자연 생성 경로 그대로다(§4.11 무회귀)")
    void zoneWithoutSetpointKeepsNaturalGeneration() {
        Fixture fixture = fixture("목표없음");
        long sensorId = createSensor(fixture, "온도센서");
        seedPreviousReading(fixture, sensorId, 10.0);

        sensorSimulatorService.tick();

        SensorReading reading = latestReading(sensorId);
        double natural = SensorSimulationProfile.simulate(
                SensorMetric.TEMPERATURE, 1, sensorId, reading.getMeasuredAt());
        assertThat(reading.getValue()).isEqualTo(natural, within(1e-9));
    }

    // ── 픽스처 ─────────────────────────────────────────────

    private record Fixture(Long ownerId, Long farmId, Long zoneId) {
    }

    private Fixture fixture(String label) {
        Long ownerId = authService.signup(new SignupRequest(
                "sim-control-" + UUID.randomUUID() + "@example.com", "password123", label)).id();
        Long farmId = farmService.createFarm(ownerId,
                new FarmRequest(label + " 농장", CropType.TOMATO, null)).id();
        Long zoneId = zoneService.createZone(farmId, ownerId, new ZoneRequest("A동", null)).id();
        return new Fixture(ownerId, farmId, zoneId);
    }

    private long createSensor(Fixture fixture, String name) {
        return deviceService.createDevice(fixture.farmId(), fixture.ownerId(), new DeviceRequest(
                fixture.zoneId(), null, null, name, DeviceKind.SENSOR,
                null, null, null, null, null, List.of(SensorMetric.TEMPERATURE))).id();
    }

    private long createController(Fixture fixture, String name) {
        return deviceService.createDevice(fixture.farmId(), fixture.ownerId(), new DeviceRequest(
                fixture.zoneId(), null, null, name, DeviceKind.CONTROLLER,
                null, null, null, null, null, null)).id();
    }

    /** 직전 tick의 측정값 — 수렴 시작점이 결정적이어야 기대값을 정확히 계산할 수 있다. */
    private void seedPreviousReading(Fixture fixture, long deviceId, double value) {
        sensorReadingRepository.saveAndFlush(SensorReading.builder()
                .farmId(fixture.farmId())
                .deviceId(deviceId)
                .zoneId(fixture.zoneId())
                .metric(SensorMetric.TEMPERATURE)
                .value(value)
                .measuredAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusMinutes(1))
                .source(SensorSource.SIMULATED)
                .build());
    }

    /** 목표값은 반드시 적용(apply)을 거쳐야 반영된다 — 큐에만 쌓인 값은 시뮬레이터에 영향이 없다. */
    private void applySetpoint(Fixture fixture, SensorMetric metric, double target) {
        long changeId = controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlChangeRequest(ControlChangeKind.SETPOINT, metric, target, null, null)).id();
        controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlApplyRequest(List.of(changeId)));
    }

    /**
     * 장비 끄기 — MANUAL 전환(목표값이 있다면 이미 적용된 뒤다) 후 장비 토글 큐 적재·적용.
     * OFF는 제어 경로로만 설정할 수 있다(contract §4.10 사이클 3 — 레지스트리 PATCH는 C001).
     */
    private void turnOffDevice(Fixture fixture, long deviceId) {
        controlService.changeMode(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlModeRequest(OperationMode.MANUAL));
        long changeId = controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlChangeRequest(ControlChangeKind.DEVICE, null, null, deviceId,
                        DeviceStatus.OFF)).id();
        controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlApplyRequest(List.of(changeId)));
    }

    private SensorReading latestReading(long deviceId) {
        return sensorReadingRepository.findAll().stream()
                .filter(reading -> reading.getDeviceId().equals(deviceId))
                .max((left, right) -> left.getMeasuredAt().compareTo(right.getMeasuredAt()))
                .orElseThrow(() -> new IllegalStateException("측정값이 생성되지 않음: deviceId=" + deviceId));
    }
}
