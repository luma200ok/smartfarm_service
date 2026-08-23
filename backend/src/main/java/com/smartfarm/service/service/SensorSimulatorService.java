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

/**
 * 가상 장비 시뮬레이터 본체(contract §4.11, 이슈 #90) — {@code kind=SENSOR}이고
 * {@code status != OFFLINE}인 장비마다 1틱(호출 1회)에 <b>그 장비가 선언한 {@code metrics}
 * (§4.10 V16 사이클 2)만</b> 생성한다. 초판은 Device에 지표 선언 필드가 없어 센서 1대를 7종 복합
 * 프로브로 가정했으나(적재량 추정이 7배 틀어짐), 사이클 2에서 {@code device_metrics}가 추가되며
 * 그 선언분만 생성하도록 정정됐다.
 *
 * <p>⚠️ 위치 3종은 device의 값을 <b>그대로 복사</b>한다(zoneId/rackId/rackLevelId 유도·조합 금지 —
 * contract §4.11 필수 요건 2). device 저장 시점에 이미 {@code DeviceService}가 부모 FK를 자동
 * 채워 완전한 삼중조로 저장하므로(§4.10 사이클 2), 여기서는 그 값을 다시 유도하지 않고 복사만 한다.
 *
 * <p>⚠️ {@code tick()} 자체엔 {@code @Transactional}을 걸지 않는다(사이클 2 리뷰 P2-4) — 저장은
 * 농장별로 {@link SensorSimulatorPersistenceService#saveForFarm}에 위임해 농장 하나당 트랜잭션
 * 하나가 되게 한다(전 농장을 한 트랜잭션·한 힙에 올리지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorSimulatorService {

    private final DeviceRepository deviceRepository;
    private final RackLevelRepository rackLevelRepository;
    private final SensorSimulatorPersistenceService sensorSimulatorPersistenceService;

    /** 농장당 1틱 최대 생성 행 수(contract §4.11 사이클 2 정정 — 센서 대수가 아니라 행 수 기준). */
    @Value("${sensor-simulator.max-rows-per-farm:300}")
    private int maxRowsPerFarm;

    /**
     * 1틱 전체(전 농장 합산) 최대 생성 행 수(contract §4.11 사이클 2 리뷰 P2-4) — 농장당 상한만
     * 있으면 농장 생성 상한이 아직 없는 상태(#91)에서 농장 수만큼 곱해져 무한정 커진다. 농장은
     * id 오름차순으로 처리하며 이 전역 예산을 다 쓰면 이후 농장은 이번 틱에서 건너뛰고 WARN을
     * 남긴다(농장당 상한과 동일하게 조용히 자르지 않는다).
     */
    @Value("${sensor-simulator.max-rows-per-tick:2000}")
    private int maxRowsPerTick;

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

        int remainingGlobalRows = maxRowsPerTick;
        for (Map.Entry<Long, List<Device>> entry : byFarm.entrySet()) {
            Long farmId = entry.getKey();
            if (remainingGlobalRows <= 0) {
                log.warn("센서 시뮬레이터 — 1틱 전역 상한({}행) 소진, 농장 {}은(는) 이번 틱에서 생성하지 않음",
                        maxRowsPerTick, farmId);
                continue;
            }

            int farmCap = Math.min(maxRowsPerFarm, remainingGlobalRows);
            List<SensorReading> readings = generateForFarm(farmId, entry.getValue(), levelsById, measuredAt, farmCap);
            remainingGlobalRows -= readings.size();
            sensorSimulatorPersistenceService.saveForFarm(readings);
        }
    }

    /**
     * 상한(이번 호출에 허용된 {@code effectiveCap}행 — 농장당 상한과 남은 전역 예산 중 작은 값)은
     * <b>id 오름차순으로 누적 행 수를 채우다가</b> 다음 장비를 더하면 상한을 넘는 시점에서 멈춘다
     * (조용히 자르지 않고 WARN 로그 — contract §4.11). 장비마다 생성 행 수가 {@code metrics.size()}로
     * 다르므로 장비 개수가 아니라 누적 행 수로 판단해야 한다(사이클 2 정정 — 초판은 장비 개수 기준이라
     * 지표 수만큼 곱해져 추정이 틀어졌다).
     */
    private List<SensorReading> generateForFarm(Long farmId, List<Device> devices,
                                                 Map<Long, RackLevel> levelsById, LocalDateTime measuredAt,
                                                 int effectiveCap) {
        List<SensorReading> readings = new ArrayList<>();
        int generatedRows = 0;
        int processedDevices = 0;

        for (Device device : devices) {
            int rowsForDevice = device.getMetrics().size();
            if (generatedRows + rowsForDevice > effectiveCap) {
                break;
            }

            RackLevel level = device.getRackLevelId() != null ? levelsById.get(device.getRackLevelId()) : null;
            int levelNo = level != null ? level.getLevelNo() : 1;
            for (SensorMetric metric : device.getMetrics()) {
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
            generatedRows += rowsForDevice;
            processedDevices++;
        }

        int skippedDevices = devices.size() - processedDevices;
        if (skippedDevices > 0) {
            log.warn("농장 {} 센서 시뮬레이터 — 1틱 생성 행 수 상한({}행, 농장당·전역 중 작은 값) 초과, "
                            + "장비 {}개 중 {}개만 생성하고 {}개는 이번 틱에서 생성하지 않음(누적 {}행)",
                    farmId, effectiveCap, devices.size(), processedDevices, skippedDevices, generatedRows);
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
