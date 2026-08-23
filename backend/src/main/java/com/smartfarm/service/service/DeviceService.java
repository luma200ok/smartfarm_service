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
import java.util.List;
import java.util.Map;
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
        validateLocation(farmId, request.zoneId(), request.rackId(), request.rackLevelId());

        if (request.serial() != null && deviceRepository.existsByFarmIdAndSerial(farmId, request.serial())) {
            throw new CustomException(ErrorCode.E002);
        }

        try {
            Device device = deviceRepository.save(Device.builder()
                    .farmId(farmId)
                    .zoneId(request.zoneId())
                    .rackId(request.rackId())
                    .rackLevelId(request.rackLevelId())
                    .name(request.name())
                    .kind(request.kind())
                    .model(request.model())
                    .serial(request.serial())
                    .status(request.status())
                    .calibrationDueAt(request.calibrationDueAt())
                    .installedOn(request.installedOn())
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
        // (contract §4.10 리뷰 반영 — 요청값만 보면 새지 않은 것처럼 통과해버린다).
        Long effectiveZoneId = request.zoneId() != null ? request.zoneId() : device.getZoneId();
        Long effectiveRackId = request.rackId() != null ? request.rackId() : device.getRackId();
        Long effectiveRackLevelId = request.rackLevelId() != null ? request.rackLevelId() : device.getRackLevelId();
        validateLocation(farmId, effectiveZoneId, effectiveRackId, effectiveRackLevelId);

        if (request.serial() != null
                && deviceRepository.existsByFarmIdAndSerialAndIdNot(farmId, request.serial(), device.getId())) {
            throw new CustomException(ErrorCode.E002);
        }

        try {
            device.update(request.zoneId(), request.rackId(), request.rackLevelId(), request.name(),
                    request.kind(), request.model(), request.serial(), request.status(),
                    request.calibrationDueAt(), request.installedOn());
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

    /**
     * 위치 FK 3종이 제공된 경우 (1) 그 리소스가 이 농장 소속인지 재확인(cross-tenant IDOR 차단),
     * (2) 서로의 계층 관계가 일치하는지 확인한다 — 쌍 2개(① rack.zoneId==zoneId ② level.rackId==
     * rackId)만으로는 부족하다(2차 리뷰 반영, contract §4.10): {@code rackId}를 생략하면 두 쌍
     * 검사가 전부 skip돼 {@code {zoneId: A동, rackId: null, rackLevelId: B동 랙의 층}}이 통과해
     * 버린다. ③ **전이** 검사로 {@code rackId}가 없어도 level → rack → zone을 따라가 zoneId와
     * 대조한다. 불일치 시 C001 — §4.11 SensorReading이 이 3종을 device에서 유도해 비정규화하므로,
     * 모순된 삼중조를 여기서 막지 않으면 측정값 테이블까지 오염된다.
     */
    private void validateLocation(Long farmId, Long zoneId, Long rackId, Long rackLevelId) {
        if (zoneId != null) {
            zoneRepository.findByIdAndFarmId(zoneId, farmId).orElseThrow(() -> new CustomException(ErrorCode.R001));
        }

        Rack rack = null;
        if (rackId != null) {
            rack = rackRepository.findByIdAndFarmId(rackId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R002));
            if (zoneId != null && !rack.getZoneId().equals(zoneId)) {
                throw new CustomException(ErrorCode.C001, "장비 위치가 어긋납니다 — 랙이 지정한 존 소속이 아닙니다.");
            }
        }

        if (rackLevelId != null) {
            RackLevel level = rackLevelRepository.findByIdAndFarmId(rackLevelId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R003));
            if (rackId != null && !level.getRackId().equals(rackId)) {
                throw new CustomException(ErrorCode.C001, "장비 위치가 어긋납니다 — 층이 지정한 랙 소속이 아닙니다.");
            }
            // 전이 검사(③) — rackId가 생략된 경우만 추가 조회(rackId가 있었으면 위 ①에서 이미
            // rack.zoneId==zoneId를 확인했으므로 중복 조회하지 않는다).
            if (zoneId != null && rackId == null) {
                Rack levelRack = rackRepository.findByIdAndFarmId(level.getRackId(), farmId)
                        .orElseThrow(() -> new CustomException(ErrorCode.R002));
                if (!levelRack.getZoneId().equals(zoneId)) {
                    throw new CustomException(ErrorCode.C001, "장비 위치가 어긋납니다 — 층이 지정한 존 소속이 아닙니다.");
                }
            }
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
