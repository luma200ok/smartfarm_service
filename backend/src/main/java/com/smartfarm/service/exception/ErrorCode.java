package com.smartfarm.service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    C001(HttpStatus.BAD_REQUEST, "C001", "요청 값이 올바르지 않습니다."),
    C002(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "내부 서버 오류가 발생했습니다."),
    C003(HttpStatus.NOT_FOUND, "C003", "존재하지 않는 경로입니다."),
    C004(HttpStatus.METHOD_NOT_ALLOWED, "C004", "허용되지 않는 메서드입니다."),

    // Auth
    A001(HttpStatus.CONFLICT, "A001", "이미 사용 중인 이메일입니다."),
    A002(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 일치하지 않습니다."),
    A003(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다."),
    A004(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 토큰입니다."),
    A005(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
