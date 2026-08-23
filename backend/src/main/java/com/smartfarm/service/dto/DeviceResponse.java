package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        LocalDateTime createdAt
) {

    public static DeviceResponse from(Device device) {
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
                device.getCreatedAt()
        );
    }
}
