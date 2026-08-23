package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Device;
import java.util.List;

public record DeviceListResponse(List<DeviceResponse> devices) {

    public static DeviceListResponse of(List<Device> devices) {
        return new DeviceListResponse(devices.stream().map(DeviceResponse::from).toList());
    }
}
