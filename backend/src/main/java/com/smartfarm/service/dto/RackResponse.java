package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Rack;
import java.time.LocalDateTime;

public record RackResponse(
        Long id,
        Long zoneId,
        String code,
        Integer levelCount,
        Integer displayOrder,
        LocalDateTime createdAt
) {

    public static RackResponse from(Rack rack) {
        return new RackResponse(rack.getId(), rack.getZoneId(), rack.getCode(), rack.getLevelCount(),
                rack.getDisplayOrder(), rack.getCreatedAt());
    }
}
