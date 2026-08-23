package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DeviceResponse(
        Long id,
        Long zoneId,
        Long rackId,
        Long rackLevelId,
        String name,
        DeviceKind kind,
        String model,
        String serial,
        DeviceStatus status,
        LocalDateTime lastSeenAt,
        LocalDateTime calibrationDueAt,
        LocalDate installedOn,
        List<SensorMetric> metrics,
        LocalDateTime createdAt
) {

    public static DeviceResponse from(Device device) {
        // metrics는 저장 순서가 보장되지 않는 @ElementCollection(Set)이라 응답 결정성을 위해
        // SensorMetric 선언 순서(enum ordinal)로 정렬한다.
        List<SensorMetric> sortedMetrics = device.getMetrics().stream().sorted().toList();
        return new DeviceResponse(
                device.getId(),
                device.getZoneId(),
                device.getRackId(),
                device.getRackLevelId(),
                device.getName(),
                device.getKind(),
                device.getModel(),
                device.getSerial(),
                device.getStatus(),
                device.getLastSeenAt(),
                device.getCalibrationDueAt(),
                device.getInstalledOn(),
                sortedMetrics,
                device.getCreatedAt()
        );
    }
}
