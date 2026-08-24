package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmSeverity;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 알람 통계(이슈 #116) — 최근 N일 severity별 건수 + 평균 처리시간(occurredAt→acknowledgedAt, 분
 * 단위). acknowledgedAt이 없는(미확인) 이벤트는 평균 계산에서 제외한다. 확인된 이벤트가 하나도
 * 없으면 {@code avgAcknowledgeMinutes}는 null.
 */
public record AlarmStatsResponse(
        int days,
        Map<AlarmSeverity, Long> countBySeverity,
        Double avgAcknowledgeMinutes
) {

    public static AlarmStatsResponse of(int days, List<AlarmEvent> events) {
        Map<AlarmSeverity, Long> countBySeverity = new EnumMap<>(AlarmSeverity.class);
        for (AlarmSeverity severity : AlarmSeverity.values()) {
            countBySeverity.put(severity, 0L);
        }
        for (AlarmEvent event : events) {
            countBySeverity.merge(event.getSeverity(), 1L, Long::sum);
        }

        List<AlarmEvent> acknowledged = events.stream()
                .filter(e -> e.getAcknowledgedAt() != null)
                .toList();
        Double avgAcknowledgeMinutes = acknowledged.isEmpty() ? null
                : acknowledged.stream()
                        .mapToLong(e -> Duration.between(e.getOccurredAt(), e.getAcknowledgedAt()).toMinutes())
                        .average()
                        .orElse(0.0);

        return new AlarmStatsResponse(days, countBySeverity, avgAcknowledgeMinutes);
    }
}
