package com.smartfarm.service.service;

import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.ControlSetpointRepository;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.ReadingLatestValueProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시뮬레이터 tick의 제어 반영 입력 조회(contract §4.12 시뮬레이터 연동) — 농장당 <b>최대 3쿼리</b>로
 * 끝낸다(목표값 / 꺼진 제어기 / 직전 값). 목표값이 하나도 없는 농장은 첫 쿼리에서 끝나고 나머지는
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

        // 꺼진 제어기가 있는 존은 목표로 수렴시키지 않는다(제어기가 꺼졌으니 자연 표류 — §4.12).
        Set<Long> uncontrolledZoneIds = deviceRepository
                .findByFarmIdAndKindAndStatus(farmId, DeviceKind.CONTROLLER, DeviceStatus.OFF).stream()
                .map(Device::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 직전 값은 "목표가 실제로 걸리는 존"의 센서만 필요하다 — 그 외 장비까지 조회하면 농장 전체
        // 최신값을 매 틱 끌어오게 된다.
        List<Long> deviceIds = sensorDevices.stream()
                .filter(device -> device.getZoneId() != null)
                .filter(device -> targetsByZone.containsKey(device.getZoneId()))
                .filter(device -> !uncontrolledZoneIds.contains(device.getZoneId()))
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
