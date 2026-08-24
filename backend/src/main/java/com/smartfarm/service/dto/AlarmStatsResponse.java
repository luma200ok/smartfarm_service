package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmSeverity;
import java.util.EnumMap;
import java.util.Map;

/**
 * 알람 통계(이슈 #116) — 최근 N일 severity별 건수 + 평균 처리시간(occurredAt→acknowledgedAt, 분
 * 단위). acknowledgedAt이 없는(미확인) 이벤트는 평균 계산에서 제외한다. 확인된 이벤트가 하나도
 * 없으면 {@code avgAcknowledgeMinutes}는 null.
 *
 * <p>집계는 서비스 계층이 DB 쿼리로 수행한다(이슈 #116 리뷰 P2-C — 엔티티 전량 로딩 후 메모리
 * 연산은 알람이 쌓인 농장에서 힙 압박을 유발할 수 있어 제거). 이 팩토리는 이미 집계된 결과만
 * 받아 빠진 severity를 0으로 채워 넣는다.
 */
public record AlarmStatsResponse(
        int days,
        Map<AlarmSeverity, Long> countBySeverity,
        Double avgAcknowledgeMinutes
) {

    public static AlarmStatsResponse of(int days, Map<AlarmSeverity, Long> countBySeverity,
                                         Double avgAcknowledgeMinutes) {
        Map<AlarmSeverity, Long> normalized = new EnumMap<>(AlarmSeverity.class);
        for (AlarmSeverity severity : AlarmSeverity.values()) {
            normalized.put(severity, countBySeverity.getOrDefault(severity, 0L));
        }
        return new AlarmStatsResponse(days, normalized, avgAcknowledgeMinutes);
    }
}
