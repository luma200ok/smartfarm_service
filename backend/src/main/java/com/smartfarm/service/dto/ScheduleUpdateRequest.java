package com.smartfarm.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

/**
 * 스케줄 부분 수정 요청(이슈 #129-C) — null 필드는 미변경(AlarmRuleUpdateRequest와 동일 패턴).
 *
 * <p>{@code zoneId}·{@code actionType}은 <b>수정 대상이 아니다</b> — 스케줄의 정체성이라 바꾸려면
 * 새로 만들고 기존을 지운다({@code Schedule#update} 참고).
 */
public record ScheduleUpdateRequest(
        @Size(max = 50) String name,
        Boolean enabled,
        @Size(max = 100) String cronExpression,
        JsonNode actionPayload
) {
}
