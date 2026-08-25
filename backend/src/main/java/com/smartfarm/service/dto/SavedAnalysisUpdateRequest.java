package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 저장한 분석 부분 수정 요청(이슈 #126) — <b>이름(rename)만</b> 허용한다.
 *
 * <p>{@code metrics}·{@code range}·{@code scopeType}·{@code scopeId}는 PATCH 대상이 아니다
 * ({@code AlarmRuleUpdateRequest}와 동일 원칙 — 그 넷은 "무엇을 어디서 보는가"라는 저장물의
 * 정체성이고, 바꾸려면 새로 만들고 기존 것을 지우는 것이 맞다). 이렇게 두면 스코프·지표 조합을
 * 다시 검증·병합하는 복잡도도 함께 사라진다 — 바꾸고 싶으면 삭제 후 재생성한다.
 */
public record SavedAnalysisUpdateRequest(
        @NotBlank @Size(max = 50) String name
) {
}
