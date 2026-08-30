package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEventLog;
import java.util.List;

/**
 * 알람 이벤트 상세 + 타임라인(AlarmEventLog 목록, 이슈 #116). {@code event}는 스코프 라벨·규칙
 * 요약·처리자 이름까지 조립된 {@link AlarmEventResponse}를 그대로 받는다(이슈 #135) — 이 레코드는
 * 조립하지 않는다(그 조립은 배치 조회가 필요해 {@code AlarmEventService}의 책임).
 */
public record AlarmEventDetailResponse(
        AlarmEventResponse event,
        List<AlarmEventLogResponse> timeline
) {

    public static AlarmEventDetailResponse of(AlarmEventResponse event, List<AlarmEventLog> logs) {
        return new AlarmEventDetailResponse(
                event,
                logs.stream().map(AlarmEventLogResponse::from).toList()
        );
    }
}
