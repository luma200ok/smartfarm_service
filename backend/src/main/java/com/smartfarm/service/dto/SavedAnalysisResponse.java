package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.SavedAnalysis;
import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDateTime;
import java.util.List;

/** 저장한 분석 응답(이슈 #126) — {@code metrics}는 저장된 JSON을 파싱한 값(서비스가 채워 넣는다). */
public record SavedAnalysisResponse(
        Long id,
        Long farmId,
        String name,
        List<SensorMetric> metrics,
        String range,
        AlarmScopeType scopeType,
        Long scopeId,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SavedAnalysisResponse from(SavedAnalysis analysis, List<SensorMetric> metrics) {
        return new SavedAnalysisResponse(
                analysis.getId(),
                analysis.getFarmId(),
                analysis.getName(),
                metrics,
                analysis.getRange(),
                analysis.getScopeType(),
                analysis.getScopeId(),
                analysis.getCreatedBy(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt()
        );
    }
}
