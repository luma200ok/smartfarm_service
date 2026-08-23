package com.smartfarm.service.dto;

import com.smartfarm.service.entity.ControlApplyLog;
import java.time.LocalDateTime;
import java.util.List;

/** 최근 적용 이력(프리뷰 "최근 적용", contract §4.12). */
public record ControlApplyLogResponse(
        Long id,
        String summary,
        Integer itemCount,
        Long appliedBy,
        LocalDateTime appliedAt
) {

    public static ControlApplyLogResponse from(ControlApplyLog log) {
        return new ControlApplyLogResponse(log.getId(), log.getSummary(), log.getItemCount(),
                log.getAppliedBy(), log.getAppliedAt());
    }

    public static List<ControlApplyLogResponse> from(List<ControlApplyLog> logs) {
        return logs.stream().map(ControlApplyLogResponse::from).toList();
    }
}
