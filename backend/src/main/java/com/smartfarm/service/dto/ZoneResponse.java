package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Zone;
import java.time.LocalDateTime;

public record ZoneResponse(
        Long id,
        String name,
        Integer displayOrder,
        LocalDateTime createdAt
) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getName(), zone.getDisplayOrder(), zone.getCreatedAt());
    }
}
