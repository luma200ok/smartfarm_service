package com.smartfarm.service.service;

import com.smartfarm.service.entity.ControlMode;
import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.ControlModeRepository;
import com.smartfarm.service.repository.ControlSetpointRepository;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.ReadingLatestValueProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시뮬레이터 tick의 제어 반영 입력 조회(contract §4.12 시뮬레이터 연동) — 농장당 <b>최대 4쿼리</b>로
 * 끝낸다(목표값 / 꺼진 제어기 / 운전 모드 / 직전 값). 목표값이 하나도 없는 농장은 첫 쿼리에서 끝나고 나머지는
 * 아예 실행되지 않는다 — 제어를 쓰지 않는 농장에 tick 비용을 늘리지 않기 위함이다.
 *
 * <p>{@code SensorSimulatorService}에서 분리한 이유: 시뮬레이터 tick은 {@code @Transactional}을 걸지
 * 않는 비-트랜잭션 경로라(§4.11 사이클 2 리뷰 P2-4) 조회 책임을 섞으면 읽기 범위가 흐려진다. 여기서는
 * 읽기만 하고, 쓰기는 여전히 {@code SensorSimulatorPersistenceService}가 농장 단위 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class ControlSimulationContextProvider {

    private final ControlSetpointRepository controlSetpointRepository;
    private final ControlModeRepository controlModeRepository;
    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository sensorReadingRepository;

    /**
     * @param farmId        대상 농장
     * @param sensorDevices 이번 틱에 값을 만들 센서 장비(직전 값 조회 대상을 이 목록으로 좁힌다)
     */
    public ControlSimulationContext forFarm(Long farmId, List<Device> sensorDevices) {
        List<ControlSetpoint> setpoints = controlSetpointRepository.findByFarmId(farmId);
        if (setpoints.isEmpty()) {
            return ControlSimulationContext.empty();
        }

        Map<Long, Map<SensorMetric, Double>> targetsByZone = new HashMap<>();
        for (ControlSetpoint setpoint : setpoints) {
            targetsByZone.computeIfAbsent(setpoint.getZoneId(), key -> new HashMap<>())
                    .put(setpoint.getMetric(), setpoint.getTargetValue());
        }

        // 수렴 해제 존(contract §4.12) — 두 조건 중 하나면 목표로 끌지 않고 자연값으로 표류시킨다.
        Set<Long> uncontrolledZoneIds = new HashSet<>();
        // ① 꺼진 제어기가 있는 존 — 제어기가 꺼졌으니 목표를 유지할 수단이 없다.
        deviceRepository.findByFarmIdAndKindAndStatus(farmId, DeviceKind.CONTROLLER, DeviceStatus.OFF).stream()
                .map(Device::getZoneId)
                .filter(Objects::nonNull)
                .forEach(uncontrolledZoneIds::add);
        // ② MANUAL 존(2026-08-24 2차 리뷰) — ①만 보면 제어기가 <b>없는</b> 존과 제어기가 OFFLINE인
        //    존은 비상 정지 후에도 계속 목표로 수렴한다(끌 대상이 없거나 정지 대상에서 빠지므로).
        //    UI는 "정지 완료"인데 그래프만 끌려가 안전 기능의 의미가 존의 장비 구성에 따라 달라졌다.
        //    §4.12 모드 표가 이미 "MANUAL에서는 목표값 편집 거부"라 이 규칙은 계약 정합화다.
        controlModeRepository.findByFarmId(farmId).stream()
                .filter(mode -> mode.getMode() == OperationMode.MANUAL)
                .map(ControlMode::getZoneId)
                .forEach(uncontrolledZoneIds::add);

        // 직전 값은 "목표가 설정된 존"의 센서만 필요하다 — 그 외 장비까지 조회하면 농장 전체 최신값을
        // 매 틱 끌어오게 된다. 제어기가 꺼진 존도 포함한다: 목표 수렴이 해제되면 자연값으로 되돌아가는
        // 것도 직전 값에서 출발해야 계단이 생기지 않는다(ControlSimulationContext#isReleased).
        List<Long> deviceIds = sensorDevices.stream()
                .filter(device -> device.getZoneId() != null)
                .filter(device -> targetsByZone.containsKey(device.getZoneId()))
                .map(Device::getId)
                .toList();
        Map<ControlSimulationContext.DeviceMetricKey, Double> previousValues = deviceIds.isEmpty()
                ? Map.of()
                : toPreviousValues(sensorReadingRepository.findLatestValueByDeviceIds(deviceIds));

        return new ControlSimulationContext(targetsByZone, uncontrolledZoneIds, previousValues);
    }

    private Map<ControlSimulationContext.DeviceMetricKey, Double> toPreviousValues(
            List<ReadingLatestValueProjection> rows) {
        Map<ControlSimulationContext.DeviceMetricKey, Double> previousValues = new HashMap<>();
        for (ReadingLatestValueProjection row : rows) {
            previousValues.put(
                    new ControlSimulationContext.DeviceMetricKey(row.getDeviceId(),
                            SensorMetric.valueOf(row.getMetric())),
                    row.getValue());
        }
        return previousValues;
    }
}
