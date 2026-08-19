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
    A005(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),

    // Farm
    F001(HttpStatus.NOT_FOUND, "F001", "농장을 찾을 수 없습니다."),
    F002(HttpStatus.FORBIDDEN, "F002", "농장 멤버가 아닙니다."),
    F003(HttpStatus.FORBIDDEN, "F003", "농장 소유자 권한이 필요합니다."),
    F004(HttpStatus.BAD_REQUEST, "F004", "유효하지 않거나 만료된 초대코드입니다."),
    F005(HttpStatus.CONFLICT, "F005", "이미 농장 멤버입니다."),
    F006(HttpStatus.BAD_REQUEST, "F006", "농장 소유자는 탈퇴할 수 없습니다. 농장 삭제만 가능합니다."),

    // Diagnosis
    D001(HttpStatus.NOT_FOUND, "D001", "진단 이력을 찾을 수 없습니다."),
    D002(HttpStatus.BAD_REQUEST, "D002", "이미지 형식 또는 크기가 올바르지 않습니다."),
    D003(HttpStatus.BAD_GATEWAY, "D003", "AI 서버 오류가 발생했습니다."),

    // Prescription
    P001(HttpStatus.NOT_FOUND, "P001", "처방 이력을 찾을 수 없습니다."),
    P002(HttpStatus.INTERNAL_SERVER_ERROR, "P002", "처방 생성에 실패했습니다."),
    P003(HttpStatus.TOO_MANY_REQUESTS, "P003", "AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
