package com.smartfarm.service.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 상세 메시지를 붙여야 하는 예외용(예: N003 — "어떤 성분이 초과인지" 등 원인을 응답에 실어야
     * 안전 판단에 도움이 되는 경우). {@link GlobalExceptionHandler}가 {@code e.getMessage()}를
     * 그대로 응답 body의 message로 사용하므로, 단일 인자 생성자(기존 전 도메인 사용 패턴)와
     * 완전히 하위 호환된다 — 단일 인자 생성자는 여전히 {@code errorCode.getMessage()}만 싣는다.
     */
    public CustomException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
