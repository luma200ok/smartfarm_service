package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventLog;
import java.util.List;

/** 알람 이벤트 상세 + 타임라인(AlarmEventLog 목록, 이슈 #116). */
public record AlarmEventDetailResponse(
        AlarmEventResponse event,
        List<AlarmEventLogResponse> timeline
) {

    public static AlarmEventDetailResponse of(AlarmEvent event, List<AlarmEventLog> logs) {
        return new AlarmEventDetailResponse(
                AlarmEventResponse.from(event),
                logs.stream().map(AlarmEventLogResponse::from).toList()
        );
    }
}
