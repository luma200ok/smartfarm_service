package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.util.List;

/**
 * 제어 화면의 존 소속 장비 상태(contract §4.12) — 조작 대상 선택·통신두절 안내(CT002)에 필요한
 * 최소 필드만 싣는다(전체 속성은 §4.10 {@code DeviceResponse}).
 */
public record ControlDeviceResponse(
        Long id,
        String name,
        DeviceKind kind,
        DeviceStatus status
) {

    public static ControlDeviceResponse from(Device device) {
        return new ControlDeviceResponse(device.getId(), device.getName(), device.getKind(), device.getStatus());
    }

    public static List<ControlDeviceResponse> from(List<Device> devices) {
        return devices.stream().map(ControlDeviceResponse::from).toList();
    }
}
