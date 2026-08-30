package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import java.time.LocalDateTime;

/**
 * 알람 이벤트 응답(contract §4.13). {@code ruleId}/{@code scopeType}/{@code scopeId}는 이슈 #118에서
 * 추가됐다 — 프리뷰 알람 화면의 위치 표기("군산1 · B3랙 4층")를 프런트가 조립하려면 이벤트 자체에
 * 스코프가 실려 있어야 한다. #118 이전에 생성된 과거 이벤트는 세 값 모두 null(=농장 단위)이다.
 *
 * <p>{@code scopeLabel}/{@code ruleSummary}/{@code acknowledgedByName}/{@code resolvedByName}은
 * 이슈 #135에서 추가됐다 — 이전에는 FE가 zone 트리·규칙·유저 목록을 따로 받아 직접 조합했다(#136).
 * 넷 다 <b>"없으면 null"</b>이다 — 스코프가 삭제됐거나, 규칙이 없거나(수동/시스템 발생 알람), 사용자가
 * 탈퇴했으면 서버가 "알 수 없음"·"-" 같은 문구를 지어내지 않고 null을 그대로 내려준다(표시 문구는
 * FE 판단). {@link com.smartfarm.service.service.AlarmEventService}가 목록 크기와 무관하게
 * 배치(batch) 조회로 이 값들을 조립한다(N+1 방지, #139 배치 조회 패턴 재사용).
 */
public record AlarmEventResponse(
        Long id,
        Long farmId,
        AlarmSeverity severity,
        AlarmSourceType sourceType,
        String metricKey,
        String message,
        AlarmEventStatus status,
        LocalDateTime occurredAt,
        LocalDateTime acknowledgedAt,
        Long acknowledgedBy,
        LocalDateTime resolvedAt,
        Long resolvedBy,
        Long thresholdId,
        Long ruleId,
        AlarmScopeType scopeType,
        Long scopeId,
        LocalDateTime createdAt,
        String scopeLabel,
        String ruleSummary,
        String acknowledgedByName,
        String resolvedByName
) {

    /** 스코프·규칙·유저 이름 정보 없이 이벤트 원본 필드만 담는다(표시용 부가 필드는 전부 null). */
    public static AlarmEventResponse from(AlarmEvent event) {
        return from(event, null, null, null, null);
    }

    public static AlarmEventResponse from(AlarmEvent event, String scopeLabel, String ruleSummary,
                                           String acknowledgedByName, String resolvedByName) {
        return new AlarmEventResponse(
                event.getId(),
                event.getFarmId(),
                event.getSeverity(),
                event.getSourceType(),
                event.getMetricKey(),
                event.getMessage(),
                event.getStatus(),
                event.getOccurredAt(),
                event.getAcknowledgedAt(),
                event.getAcknowledgedBy(),
                event.getResolvedAt(),
                event.getResolvedBy(),
                event.getThresholdId(),
                event.getRuleId(),
                event.getScopeType(),
                event.getScopeId(),
                event.getCreatedAt(),
                scopeLabel,
                ruleSummary,
                acknowledgedByName,
                resolvedByName
        );
    }
}
