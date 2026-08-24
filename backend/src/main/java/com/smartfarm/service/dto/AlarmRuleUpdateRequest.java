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
 *
 * <p>{@code name}은 미전송(null)이면 미변경이지만, <b>보내려면 공백일 수 없다</b>(#118 보안 리뷰
 * P3-1). 생성 DTO에만 {@code @NotBlank}가 있어 {@code {"name":"   "}}로 이름을 지울 수 있었는데,
 * 그 값은 알람 메시지와 Discord 웹훅 본문 앞머리에 그대로 실린다. 다만 여기에 {@code @NotBlank}를
 * 그대로 달 수는 없다 — 그 제약은 <b>null도 거부</b>해서 "이름을 안 보내는 부분 수정"이 전부
 * 400이 된다. 그래서 공백 거부는 {@code AlarmRuleService}가 ALR003으로 판정한다(저장 시 trim도
 * 함께 — {@code AlarmMemoRequest}의 trim 선례와 동일 원칙).
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
