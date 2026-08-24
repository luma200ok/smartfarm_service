package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmSeverity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 알람 규칙 부분 수정 요청(이슈 #118) — null 필드는 미변경(Zone/Rack PATCH와 동일 패턴).
 *
 * <p>{@code source}·{@code metric}·{@code comparator}·{@code scope}는 <b>수정 대상이 아니다</b>:
 * 그 넷은 "무엇을 어디서 어떻게 보는가"라는 규칙의 정체성이고, 바꾸면 이미 열려 있는 알람 이벤트가
 * 전혀 다른 대상을 가리키게 된다(이벤트의 {@code scopeType}/{@code scopeId} 스냅샷도 어긋난다).
 * 바꾸려면 규칙을 새로 만들고 기존 규칙을 지우는 것이 맞다.
 */
public record AlarmRuleUpdateRequest(
        @Size(max = 50) String name,
        Boolean enabled,
        Double thresholdValue,
        Double thresholdMin,
        Double thresholdMax,
        @Min(1) @Max(86400) Integer durationSeconds,
        AlarmSeverity severity
) {
}
