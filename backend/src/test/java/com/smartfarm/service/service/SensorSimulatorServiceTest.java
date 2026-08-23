package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SensorSimulatorService} 1틱 전역 상한 로직(contract §4.11 사이클 2 리뷰 P2-4) 순수 단위
 * 테스트 — {@code @SpringBootTest} 통합 테스트로는 {@code deviceRepository.findByKindAndStatusNot
 * OrderByIdAsc}가 <b>전 농장 전체</b>를 대상으로 하기 때문에, 같은 Postgres Testcontainer를 공유하는
 * 다른(비-트랜잭션) 통합 테스트가 남긴 실 데이터가 전역 예산을 먼저 소진시켜 순서 의존적으로
 * 깨지는 문제가 있었다. 순수 계산 로직이므로 레포지토리를 Mockito로 격리해 결정적으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SensorSimulatorServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private RackLevelRepository rackLevelRepository;

    @Mock
    private SensorSimulatorPersistenceService sensorSimulatorPersistenceService;

    @Test
    void tickCapsGenerationAcrossFarmsByGlobalBudget() {
        SensorSimulatorService sensorSimulatorService = new SensorSimulatorService(
                deviceRepository, rackLevelRepository, sensorSimulatorPersistenceService);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerFarm", 100);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerTick", 6);

        List<SensorMetric> fourMetrics = List.of(
                SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2, SensorMetric.EC);
        Device deviceA = sensorDevice(1L, 10L, fourMetrics); // farm 10 — id 오름차순 먼저 처리
        Device deviceB = sensorDevice(2L, 20L, fourMetrics); // farm 20 — 잔여 예산(2행) < 필요(4행)

        when(deviceRepository.findByKindAndStatusNotOrderByIdAsc(DeviceKind.SENSOR, DeviceStatus.OFFLINE))
                .thenReturn(List.of(deviceA, deviceB));

        sensorSimulatorService.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorReading>> captor = ArgumentCaptor.forClass(List.class);
        verify(sensorSimulatorPersistenceService, times(2)).saveForFarm(captor.capture());

        List<List<SensorReading>> savedPerFarmCall = captor.getAllValues();
        // farmA(10)는 전역 예산 6행 안에 4행 전부 생성되고, farmB(20)는 잔여 예산 2행이 필요 행 수
        // 4행에 못 미쳐 device 단위로 통째로 skip된다(부분 생성 금지 — generateForFarm이 device
        // 단위로만 끊는다).
        assertThat(savedPerFarmCall.get(0)).hasSize(4);
        assertThat(savedPerFarmCall.get(0)).allSatisfy(r -> assertThat(r.getFarmId()).isEqualTo(10L));
        assertThat(savedPerFarmCall.get(1)).isEmpty();
    }

    @Test
    void tickSkipsRemainingFarmsEntirelyOnceGlobalBudgetIsFullyConsumed() {
        SensorSimulatorService sensorSimulatorService = new SensorSimulatorService(
                deviceRepository, rackLevelRepository, sensorSimulatorPersistenceService);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerFarm", 100);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerTick", 2);

        List<SensorMetric> twoMetrics = List.of(SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY);
        Device deviceA = sensorDevice(1L, 10L, twoMetrics); // 정확히 전역 예산(2행) 소진
        Device deviceB = sensorDevice(2L, 20L, twoMetrics); // 잔여 예산 0 — 호출조차 되지 않아야 함

        when(deviceRepository.findByKindAndStatusNotOrderByIdAsc(DeviceKind.SENSOR, DeviceStatus.OFFLINE))
                .thenReturn(List.of(deviceA, deviceB));

        sensorSimulatorService.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorReading>> captor = ArgumentCaptor.forClass(List.class);
        // remainingGlobalRows <= 0이 되면 그 이후 농장은 saveForFarm 자체를 호출하지 않는다(불필요한
        // 빈 트랜잭션 생략) — farmA 1번만 호출돼야 한다.
        verify(sensorSimulatorPersistenceService, times(1)).saveForFarm(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void tickContinuesToLaterFarmsWhenAnEarlierFarmSaveFailsWithDuplicateKey() {
        SensorSimulatorService sensorSimulatorService = new SensorSimulatorService(
                deviceRepository, rackLevelRepository, sensorSimulatorPersistenceService);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerFarm", 100);
        ReflectionTestUtils.setField(sensorSimulatorService, "maxRowsPerTick", 100);

        List<SensorMetric> oneMetric = List.of(SensorMetric.TEMPERATURE);
        Device deviceA = sensorDevice(1L, 10L, oneMetric); // 먼저 처리 — 저장이 실패한다고 가정
        Device deviceB = sensorDevice(2L, 20L, oneMetric); // 뒤 순서 — 이 농장까지 계속 처리돼야 함

        when(deviceRepository.findByKindAndStatusNotOrderByIdAsc(DeviceKind.SENSOR, DeviceStatus.OFFLINE))
                .thenReturn(List.of(deviceA, deviceB));
        // farmA 저장 시 V17 unique(device_id, metric, measured_at) 위반을 시뮬레이션(다중 인스턴스
        // 겹침·NTP 역방향 보정 등으로 인한 중복 tick) — catch 없이 두면 이 예외가 tick()의 for 루프를
        // 그대로 뚫고 나가 farmB(뒤 순서)가 통째로 유실된다(사이클 2 리뷰 P2-B).
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .doNothing()
                .when(sensorSimulatorPersistenceService).saveForFarm(any());

        sensorSimulatorService.tick();

        // farmA 실패로 예외를 던졌어도 farmB는 여전히 저장 시도된다(농장 간 격리가 실제로 동작함을
        // 검증하는 유일한 테스트 — SensorSimulatorPersistenceService를 별도 빈으로 뗀 이유 자체).
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorReading>> captor = ArgumentCaptor.forClass(List.class);
        verify(sensorSimulatorPersistenceService, times(2)).saveForFarm(captor.capture());
        assertThat(captor.getAllValues().get(1)).hasSize(1);
        assertThat(captor.getAllValues().get(1).get(0).getFarmId()).isEqualTo(20L);
    }

    private Device sensorDevice(long id, long farmId, List<SensorMetric> metrics) {
        Device device = Device.builder()
                .farmId(farmId)
                .name("센서-" + id)
                .kind(DeviceKind.SENSOR)
                .status(DeviceStatus.NORMAL)
                .metrics(new java.util.LinkedHashSet<>(metrics))
                .build();
        ReflectionTestUtils.setField(device, "id", id);
        return device;
    }
}
