package com.smartfarm.service.service;

import com.smartfarm.service.dto.DeviceListResponse;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.DeviceResponse;
import com.smartfarm.service.dto.DeviceSummaryResponse;
import com.smartfarm.service.dto.DeviceSummaryResponse.ByModel;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.Rack;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비/센서 레지스트리 CRUD(contract §4.10, 이슈 #89). 개별 장비 단위로 저장하고, 제품군 집계는
 * {@link #summary}가 서버에서 계산한다(개체 저장·집계는 서버 원칙 — handoff).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    /** V14의 unique(farm_id, serial)(활성 행) 제약명 — race 판정에 사용(InvitationService 선례). */
    static final String DEVICE_SERIAL_UNIQUE_CONSTRAINT = "ux_devices_farm_id_serial_active";

    /** calibrationDueSoon 판정 기준(contract §4.10 — 30일 이내, 이미 지난 것도 포함). */
    private static final int CALIBRATION_DUE_SOON_DAYS = 30;

    /** byModel 집계에서 그룹 상태를 대표할 심각도 우선순위(왼쪽일수록 심각). */
    private static final List<DeviceStatus> STATUS_SEVERITY =
            List.of(DeviceStatus.FAULT, DeviceStatus.OFFLINE, DeviceStatus.WARNING, DeviceStatus.NORMAL);

    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;

    public DeviceListResponse listDevices(Long farmId, Long userId, DeviceKind kind, DeviceStatus status,
                                           String q, Long zoneId) {
        farmAccessGuard.requireMember(farmId, userId);
        return DeviceListResponse.of(deviceRepository.search(farmId, kind, status, q, zoneId));
    }

    public DeviceSummaryResponse summary(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        List<Device> devices = deviceRepository.findByFarmId(farmId);

        long total = devices.size();
        long normal = devices.stream().filter(d -> d.getStatus() == DeviceStatus.NORMAL).count();
        long warning = devices.stream().filter(d -> d.getStatus() == DeviceStatus.WARNING).count();
        long faultOrOffline = devices.stream()
                .filter(d -> d.getStatus() == DeviceStatus.FAULT || d.getStatus() == DeviceStatus.OFFLINE)
                .count();

        LocalDateTime dueSoonThreshold = LocalDateTime.now().plusDays(CALIBRATION_DUE_SOON_DAYS);
        long calibrationDueSoon = devices.stream()
                .filter(d -> d.getCalibrationDueAt() != null && !d.getCalibrationDueAt().isAfter(dueSoonThreshold))
                .count();

        List<ByModel> byModel = groupByModel(devices);

        return new DeviceSummaryResponse(total, normal, warning, faultOrOffline, calibrationDueSoon, byModel);
    }

    private List<ByModel> groupByModel(List<Device> devices) {
        Map<String, List<Device>> grouped = devices.stream()
                .collect(Collectors.groupingBy(this::modelGroupKey, LinkedHashMap::new, Collectors.toList()));

        List<ByModel> result = new ArrayList<>();
        for (Map.Entry<String, List<Device>> entry : grouped.entrySet()) {
            List<Device> group = entry.getValue();
            Device representative = group.get(0);
            DeviceStatus worst = group.stream()
                    .map(Device::getStatus)
                    .min(Comparator.comparingInt(STATUS_SEVERITY::indexOf))
                    .orElse(DeviceStatus.NORMAL);
            result.add(new ByModel(representative.getModel() != null ? representative.getModel()
                    : representative.getName(), representative.getKind(), group.size(), worst));
        }
        result.sort(Comparator.comparing(ByModel::name));
        return result;
    }

    private String modelGroupKey(Device device) {
        String model = device.getModel() != null ? device.getModel() : device.getName();
        return model + "::" + device.getKind();
    }

    @Transactional
    public DeviceResponse createDevice(Long farmId, Long userId, DeviceRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);

        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException(ErrorCode.C001, "장비명은 필수입니다.");
        }
        if (request.kind() == null) {
            throw new CustomException(ErrorCode.C001, "장비 종류는 필수입니다.");
        }
        if (request.zoneId() == null && request.rackId() == null && request.rackLevelId() == null) {
            throw new CustomException(ErrorCode.C001, "위치(존·랙·층) 중 최소 하나는 필수입니다.");
        }
        ResolvedLocation location = resolveLocation(farmId, request.zoneId(), request.rackId(),
                request.rackLevelId());

        // Set.of()(불변)는 안 쓴다 — validateMetrics의 metrics.contains(null) 널 안전 검사가
        // Set.of().contains(null)에서 NPE를 던진다(사이클 2 리뷰 P3-1 회귀 — 빈 LinkedHashSet은
        // null 원소가 없어도 contains(null)이 안전하게 false를 반환한다).
        Set<SensorMetric> metrics =
                request.metrics() != null ? new LinkedHashSet<>(request.metrics()) : new LinkedHashSet<>();
        validateMetrics(request.kind(), metrics);

        if (request.serial() != null && deviceRepository.existsByFarmIdAndSerial(farmId, request.serial())) {
            throw new CustomException(ErrorCode.E002);
        }

        try {
            Device device = deviceRepository.save(Device.builder()
                    .farmId(farmId)
                    .zoneId(location.zoneId())
                    .rackId(location.rackId())
                    .rackLevelId(location.rackLevelId())
                    .name(request.name())
                    .kind(request.kind())
                    .model(request.model())
                    .serial(request.serial())
                    .status(request.status())
                    .calibrationDueAt(request.calibrationDueAt())
                    .installedOn(request.installedOn())
                    .metrics(metrics)
                    .build());
            return DeviceResponse.from(device);
        } catch (DataIntegrityViolationException e) {
            throw translateSerialViolation(e);
        }
    }

    @Transactional
    public DeviceResponse updateDevice(Long farmId, Long userId, Long deviceId, DeviceRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Device device = findDeviceOrThrow(farmId, deviceId);

        // PATCH는 부분 수정(null=미변경)이라 위치 FK 하나만 바뀌어도 나머지 기존 값과 계층이
        // 어긋날 수 있다 — 요청값과 기존 엔티티를 병합한 "최종 상태"로 정합성을 검증한다
        // (contract §4.10 리뷰 반영 — 요청값만 보면 새지 않은 것처럼 통과해버린다). resolveLocation이
        // 병합된 최종 상태에도 부모 FK 자동 채움을 동일하게 적용한다(사이클 2).
        Long effectiveZoneId = request.zoneId() != null ? request.zoneId() : device.getZoneId();
        Long effectiveRackId = request.rackId() != null ? request.rackId() : device.getRackId();
        Long effectiveRackLevelId = request.rackLevelId() != null ? request.rackLevelId() : device.getRackLevelId();
        ResolvedLocation location = resolveLocation(farmId, effectiveZoneId, effectiveRackId, effectiveRackLevelId);

        DeviceKind effectiveKind = request.kind() != null ? request.kind() : device.getKind();
        Set<SensorMetric> effectiveMetrics =
                request.metrics() != null ? new LinkedHashSet<>(request.metrics()) : device.getMetrics();
        validateMetrics(effectiveKind, effectiveMetrics);

        if (request.serial() != null
                && deviceRepository.existsByFarmIdAndSerialAndIdNot(farmId, request.serial(), device.getId())) {
            throw new CustomException(ErrorCode.E002);
        }

        try {
            device.update(location.zoneId(), location.rackId(), location.rackLevelId(), request.name(),
                    request.kind(), request.model(), request.serial(), request.status(),
                    request.calibrationDueAt(), request.installedOn(), request.metrics() != null
                            ? new LinkedHashSet<>(request.metrics()) : null);
            // update()는 dirty checking이라 flush 전엔 UPDATE SQL이 나가지 않는다 — race 감지를
            // 위해 명시적으로 즉시 flush(RackService#updateRack과 동일 패턴).
            deviceRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translateSerialViolation(e);
        }
        return DeviceResponse.from(device);
    }

    @Transactional
    public void deleteDevice(Long farmId, Long userId, Long deviceId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Device device = findDeviceOrThrow(farmId, deviceId);
        deviceRepository.delete(device);
    }

    /** {@link #resolveLocation} 반환값 — 부모 FK 자동 채움까지 반영된 최종 삼중조(사이클 2). */
    private record ResolvedLocation(Long zoneId, Long rackId, Long rackLevelId) {
    }

    /**
     * 위치 FK 3종을 해석한다(contract §4.10, 사이클 2 — 부모 FK 자동 채움). 규칙:
     * <ol>
     *   <li>주어진 값은 전부 이 농장 소속인지 재확인한다(cross-tenant IDOR 차단).</li>
     *   <li>깊은 쪽(rackLevelId → rackId → zoneId 순)이 주어지고 그 상위가 <b>주어지지 않았으면</b>
     *       상위를 유도해 함께 채운다 — 예전에는 null로 남아 §4.11 SensorReading 적재 시
     *       zoneId/rackId가 비어 존·랙 스코프 조회에서 조용히 누락됐다(사이클 2 회고).</li>
     *   <li>상위가 <b>명시적으로 함께 주어지면</b> 자동 채움 대신 계층 정합성을 검증한다(기존
     *       ①②③ 규칙 그대로 — 서로 다른 계층을 가리키면 C001). rackId 생략 시의 전이(level→rack→
     *       zone) 검사는 이제 "rackId가 없으면 항상 rack을 유도해 채운다"는 자동 채움 분기가
     *       그대로 흡수한다 — 유도된 rack의 zoneId를 명시된 zoneId와 대조하면 전이 검사와 동치다.</li>
     * </ol>
     */
    private ResolvedLocation resolveLocation(Long farmId, Long zoneId, Long rackId, Long rackLevelId) {
        RackLevel level = null;
        if (rackLevelId != null) {
            level = rackLevelRepository.findByIdAndFarmId(rackLevelId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R003));
        }

        Rack rack = null;
        if (rackId != null) {
            rack = rackRepository.findByIdAndFarmId(rackId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R002));
            if (level != null && !level.getRackId().equals(rack.getId())) {
                throw new CustomException(ErrorCode.C001, "장비 위치가 어긋납니다 — 층이 지정한 랙 소속이 아닙니다.");
            }
        } else if (level != null) {
            // 부모 FK 자동 채움 — rackId 생략, rackLevelId만 주어짐.
            rack = rackRepository.findByIdAndFarmId(level.getRackId(), farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R002));
            rackId = rack.getId();
        }

        if (zoneId != null) {
            zoneRepository.findByIdAndFarmId(zoneId, farmId).orElseThrow(() -> new CustomException(ErrorCode.R001));
            if (rack != null && !rack.getZoneId().equals(zoneId)) {
                throw new CustomException(ErrorCode.C001, "장비 위치가 어긋납니다 — 랙이 지정한 존 소속이 아닙니다.");
            }
        } else if (rack != null) {
            // 부모 FK 자동 채움 — zoneId 생략, rackId(또는 그로부터 유도된 값)가 있음.
            zoneId = rack.getZoneId();
        }

        return new ResolvedLocation(zoneId, rackId, rackLevelId);
    }

    /**
     * 장비 측정 지표 선언 검증(contract §4.10 사이클 2, V16) — {@code kind=SENSOR}는 1개 이상,
     * 그 외(CONTROLLER·GATEWAY)는 비어야 한다. 위반 시 C001. kind가 null(PATCH에서 미변경)이면
     * 호출측이 이미 기존 kind로 병합한 값을 넘기므로 이 메서드는 kind가 항상 확정된 상태로 받는다.
     *
     * <p>⚠️ {@code null} 원소 거부(사이클 2 리뷰 P3-1) — {@code metrics:["TEMPERATURE", null]}처럼
     * Jackson이 배열 안에 null을 그대로 담아 역직렬화하면 이 검사 없이는 "비었는가"만 보고
     * 통과시켜, 이후 {@code DeviceResponse.from}의 {@code .sorted()}가 null Comparable에서
     * NPE를 던지거나 DB NOT NULL 위반으로 500이 난다. 잘못된 입력에 5xx는 실패 안전 위반이므로
     * 여기서 명시적으로 C001 처리한다.
     */
    private void validateMetrics(DeviceKind kind, Set<SensorMetric> metrics) {
        if (metrics != null && metrics.contains(null)) {
            throw new CustomException(ErrorCode.C001, "측정 지표 목록에 null을 포함할 수 없습니다.");
        }
        boolean empty = metrics == null || metrics.isEmpty();
        if (kind == DeviceKind.SENSOR) {
            if (empty) {
                throw new CustomException(ErrorCode.C001, "SENSOR 장비는 측정 지표를 1개 이상 선언해야 합니다.");
            }
        } else if (!empty) {
            throw new CustomException(ErrorCode.C001, "SENSOR가 아닌 장비는 측정 지표를 선언할 수 없습니다.");
        }
    }

    /** 장비가 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 E001. */
    private Device findDeviceOrThrow(Long farmId, Long deviceId) {
        return deviceRepository.findByIdAndFarmId(deviceId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.E001));
    }

    /**
     * 같은 농장 내 시리얼 동시 등록 race → unique(farm_id, serial) 위반만 E002로 변환. 다른
     * 제약(FK 등) 위반을 오분류하지 않도록 제약명 확인 후 아니면 재throw(InvitationService 선례).
     */
    private RuntimeException translateSerialViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().contains(DEVICE_SERIAL_UNIQUE_CONSTRAINT)) {
            return new CustomException(ErrorCode.E002);
        }
        return e;
    }
}
