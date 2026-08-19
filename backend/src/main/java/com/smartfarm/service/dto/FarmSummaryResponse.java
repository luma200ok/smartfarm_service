package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmRole;

public record FarmSummaryResponse(
        Long id,
        String name,
        CropType cropType,
        FarmRole myRole
) {
}
