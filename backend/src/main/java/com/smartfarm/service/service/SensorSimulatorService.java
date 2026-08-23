package com.smartfarm.service.service;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 장비 시뮬레이터 본체(contract §4.11, 이슈 #90) — {@code kind=SENSOR}이고
 * {@code status != OFFLINE}인 장비마다 1틱(호출 1회)에 7종 지표 전부를 생성한다. Device 엔티티에는
 * "이 장비가 어떤 지표를 재는지"를 나타내는 필드가 없어(§4.10에 그런 컬럼이 없음), 센서 1대 = 복합
 * 프로브(온습도·CO2 등을 동시에 재는 통합 센서)로 간주해 SENSOR 장비마다 {@link SensorMetric}
 * 7종을 전부 생성한다 — contract의 "N × 129,600행" 예시는 지표 1종 기준 근사치이므로 실제 적재량은
 * 이보다 7배 크다(90일 보존 + 상한 200개는 그대로도 안전한 상한이라 문제는 아니나, 데이터량 추정
 * 시 참고할 것 — A 보고 사항).
 *
 * <p>⚠️ 위치 3종은 device의 값을 <b>그대로 복사</b>한다(zoneId/rackId/rackLevelId 유도·조합 금지 —
 * contract §4.11 필수 요건 2). rackLevelId가 없는 장비(게이트웨이 등)는애초에 kind=SENSOR가 아니므로
 * 대상에서 제외되지만, 데이터 무결성상 rackLevelId가 없는 SENSOR 장비는 층별 오프셋을 0으로 두고
 * 그대로 생성한다(§4.10이 SENSOR의 rackLevelId를 강제하지 않으므로 방어적으로 처리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorSimulatorService {

    private final DeviceRepository deviceRepository;
    private final RackLevelRepository rackLevelRepository;
    private final SensorReadingRepository sensorReadingRepository;

    @Value("${sensor-simulator.max-per-farm:200}")
    private int maxPerFarm;

    @Transactional
    public void tick() {
        List<Device> eligible = deviceRepository.findByKindAndStatusNotOrderByIdAsc(
                DeviceKind.SENSOR, DeviceStatus.OFFLINE);
        if (eligible.isEmpty()) {
            return;
        }

        Map<Long, RackLevel> levelsById = fetchLevels(eligible);
        LocalDateTime measuredAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        Map<Long, List<Device>> byFarm = eligible.stream()
                .collect(Collectors.groupingBy(Device::getFarmId, LinkedHashMap::new, Collectors.toList()));

        List<SensorReading> readings = new ArrayList<>();
        for (Map.Entry<Long, List<Device>> entry : byFarm.entrySet()) {
            readings.addAll(generateForFarm(entry.getKey(), entry.getValue(), levelsById, measuredAt));
        }

        if (!readings.isEmpty()) {
            sensorReadingRepository.saveAll(readings);
        }
    }

    private List<SensorReading> generateForFarm(Long farmId, List<Device> devices,
                                                 Map<Long, RackLevel> levelsById, LocalDateTime measuredAt) {
        List<Device> targets = devices;
        if (devices.size() > maxPerFarm) {
            // 상한 초과분은 생성하지 않고 WARN 로그만 남긴다(조용히 자르지 않는다 — contract §4.11).
            log.warn("농장 {} 센서 시뮬레이터 대상 {}개가 상한({}개)을 초과 — 초과분 {}개는 이번 틱에서 생성하지 않음",
                    farmId, devices.size(), maxPerFarm, devices.size() - maxPerFarm);
            targets = devices.subList(0, maxPerFarm);
        }

        List<SensorReading> readings = new ArrayList<>();
        for (Device device : targets) {
            RackLevel level = device.getRackLevelId() != null ? levelsById.get(device.getRackLevelId()) : null;
            int levelNo = level != null ? level.getLevelNo() : 1;
            for (SensorMetric metric : SensorMetric.values()) {
                double value = SensorSimulationProfile.simulate(metric, levelNo, device.getId(), measuredAt);
                readings.add(SensorReading.builder()
                        .farmId(farmId)
                        .deviceId(device.getId())
                        .zoneId(device.getZoneId())
                        .rackId(device.getRackId())
                        .rackLevelId(device.getRackLevelId())
                        .metric(metric)
                        .value(value)
                        .measuredAt(measuredAt)
                        .source(SensorSource.SIMULATED)
                        .build());
            }
        }
        return readings;
    }

    /** N+1 방지 — 대상 장비들의 rackLevelId를 한 번에 배치 조회(층별 오프셋 계산용 levelNo). */
    private Map<Long, RackLevel> fetchLevels(List<Device> devices) {
        List<Long> levelIds = devices.stream()
                .map(Device::getRackLevelId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (levelIds.isEmpty()) {
            return Map.of();
        }
        return rackLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(RackLevel::getId, Function.identity()));
    }
}
