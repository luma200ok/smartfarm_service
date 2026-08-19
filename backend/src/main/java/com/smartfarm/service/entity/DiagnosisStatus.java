package com.smartfarm.service.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 진단 상태 — ai-server의 ood_blocked(boolean)를 매핑(contract §4: status(ok|ood_blocked)).
 * JSON 직렬화 값은 계약대로 소문자 고정, DB 저장은 @Enumerated(STRING)으로 상수명 그대로("OK"/"OOD_BLOCKED") 저장한다.
 */
public enum DiagnosisStatus {

    @JsonProperty("ok")
    OK,

    @JsonProperty("ood_blocked")
    OOD_BLOCKED
}
