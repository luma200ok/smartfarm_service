package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import java.time.LocalDateTime;

/**
 * 알람 규칙 응답(이슈 #118).
 *
 * @param derived {@code true}면 §4.6 {@code PUT /env-thresholds}가 관리하는 파생 규칙이라 이 API로
 *                수정·삭제할 수 없다(ALR004). 클라이언트가 편집 UI를 잠글 수 있도록 노출한다.
 */
public record AlarmRuleResponse(
        Long id,
        Long farmId,
        String name,
        boolean enabled,
        AlarmRuleSource source,
        String metric,
        AlarmComparator comparator,
        Double thresholdValue,
        Double thresholdMin,
        Double thresholdMax,
        Integer durationSeconds,
        AlarmSeverity severity,
        AlarmScopeType scopeType,
        Long scopeId,
        boolean derived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AlarmRuleResponse from(AlarmRule rule) {
        return new AlarmRuleResponse(
                rule.getId(),
                rule.getFarmId(),
                rule.getName(),
                rule.isEnabled(),
                rule.getSource(),
                rule.getMetric(),
                rule.getComparator(),
                rule.getThresholdValue(),
                rule.getThresholdMin(),
                rule.getThresholdMax(),
                rule.getDurationSeconds(),
                rule.getSeverity(),
                rule.getScopeType(),
                rule.getScopeId(),
                rule.getThresholdId() != null,
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
