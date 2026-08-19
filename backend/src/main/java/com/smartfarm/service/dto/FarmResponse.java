package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmRole;
import java.time.LocalDateTime;

public record FarmResponse(
        Long id,
        String name,
        CropType cropType,
        String location,
        FarmRole myRole,
        long memberCount,
        LocalDateTime createdAt
) {

    public static FarmResponse of(Farm farm, FarmRole myRole, long memberCount) {
        return new FarmResponse(farm.getId(), farm.getName(), farm.getCropType(),
                farm.getLocation(), myRole, memberCount, farm.getCreatedAt());
    }
}
