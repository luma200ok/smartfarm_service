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
    /**
     * JPA {@code @Version} 낙관적 락 충돌(이슈 #116 리뷰 P3) — {@code GlobalExceptionHandler}가
     * 앱 전체의 {@code ObjectOptimisticLockingFailureException}을 잡아 매핑하는 공통 코드다. 알람
     * 이벤트 도메인(acknowledge/resolve 동시 처리)에서 처음 필요해졌지만 핸들러 자체는 특정
     * 도메인에 묶이지 않으므로 도메인 prefix(AL) 대신 Common prefix를 쓴다 — 예: {@code Device}는
     * {@code @DynamicUpdate} 컬럼 덮어쓰기 문제의 후속으로 {@code @Version} 도입이 예정돼 있는데
     * (Device 엔티티 클래스 주석 참고), 그 경우도 이 공통 코드로 응답해야 한다.
     */
    C005(HttpStatus.CONFLICT, "C005", "다른 사용자가 먼저 처리했습니다. 새로고침 후 다시 시도해주세요."),

    // Auth
    A001(HttpStatus.CONFLICT, "A001", "이미 사용 중인 이메일입니다."),
    A002(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 일치하지 않습니다."),
    A003(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다."),
    A004(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 토큰입니다."),
    A005(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),
    A006(HttpStatus.CONFLICT, "A006", "소유한 농장이 있어 탈퇴할 수 없습니다. 농장을 삭제한 후 다시 시도해주세요."),
    A007(HttpStatus.FORBIDDEN, "A007", "데모 계정에서는 이 작업을 수행할 수 없습니다."),

    // Farm
    F001(HttpStatus.NOT_FOUND, "F001", "농장을 찾을 수 없습니다."),
    F002(HttpStatus.FORBIDDEN, "F002", "농장 멤버가 아닙니다."),
    /**
     * 관리자(ADMIN) 권한 필요 — 이슈 #122로 <b>의미를 재정의</b>했다(구 "농장 소유자(OWNER) 권한 필요").
     * 구 OWNER의 권한 전량을 ADMIN이 승계하므로 판정 지점은 1:1로 대응한다. 구조 CRUD(농장·존·랙·
     * 장비·임계값·알람규칙·웹훅) · 초대 발급 · 멤버 관리/역할 변경이 이 코드를 쓴다.
     */
    F003(HttpStatus.FORBIDDEN, "F003", "농장 관리자 권한이 필요합니다."),
    F004(HttpStatus.BAD_REQUEST, "F004", "유효하지 않거나 만료된 초대코드입니다."),
    F005(HttpStatus.CONFLICT, "F005", "이미 농장 멤버입니다."),
    /**
     * 농장의 마지막 관리자 보호 — 이슈 #122로 <b>의미를 확장</b>했다(구 "OWNER는 탈퇴 불가").
     * 관리자가 0명이 되면 농장은 구조 변경·멤버 관리·삭제가 영구 불가한 관리 불능 상태가 되므로,
     * 마지막 ADMIN의 <b>강등</b>과 <b>제거(본인 탈퇴 포함)</b>를 함께 막는다. 구 계약의
     * "OWNER 본인 제거 → F006"은 이 규칙의 특수 사례(관리자가 자기 1명뿐인 농장)로 흡수된다.
     */
    F006(HttpStatus.BAD_REQUEST, "F006", "농장의 마지막 관리자는 강등하거나 제거할 수 없습니다. "
            + "다른 멤버를 관리자로 지정한 뒤 다시 시도해주세요."),
    /** 제어 권한(OPERATOR 이상) 필요 — 제어·비상 정지·알람 확인/처리·콘텐츠 작성(이슈 #122). */
    F007(HttpStatus.FORBIDDEN, "F007", "농장 제어 권한이 필요합니다."),
    /**
     * 가입 승인 대기(PENDING) — 초대 수락은 됐지만 관리자가 아직 역할을 부여하지 않았다(이슈 #122).
     * F002(멤버 아님)와 구분한다: 사용자에게 "권한이 없다"가 아니라 "승인을 기다리는 중"임을
     * 알려야 FE가 재시도·문의 안내를 띄울 수 있다.
     */
    F008(HttpStatus.FORBIDDEN, "F008", "농장 가입 승인 대기 중입니다. 관리자의 승인 후 이용할 수 있습니다."),
    /** 농장 멤버십 없음 — farm 스코프 조회라 타 농장 memberId도 여기로 떨어진다(존재 유추 차단). */
    F009(HttpStatus.NOT_FOUND, "F009", "농장 멤버를 찾을 수 없습니다."),

    // Diagnosis
    D001(HttpStatus.NOT_FOUND, "D001", "진단 이력을 찾을 수 없습니다."),
    D002(HttpStatus.BAD_REQUEST, "D002", "이미지 형식 또는 크기가 올바르지 않습니다."),
    D003(HttpStatus.BAD_GATEWAY, "D003", "AI 서버 오류가 발생했습니다."),
    D004(HttpStatus.NOT_FOUND, "D004", "진단 원본 이미지를 찾을 수 없습니다."),

    // Prescription
    P001(HttpStatus.NOT_FOUND, "P001", "처방 이력을 찾을 수 없습니다."),
    P002(HttpStatus.INTERNAL_SERVER_ERROR, "P002", "처방 생성에 실패했습니다."),
    P003(HttpStatus.TOO_MANY_REQUESTS, "P003", "AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요."),
    P004(HttpStatus.TOO_MANY_REQUESTS, "P004", "처방 대기 한도를 초과했습니다. 진행 중인 처방 완료 후 다시 시도해주세요."),

    // Farm Log
    L001(HttpStatus.NOT_FOUND, "L001", "작업일지를 찾을 수 없습니다."),
    L002(HttpStatus.FORBIDDEN, "L002", "작업일지 수정/삭제 권한이 없습니다."),

    // Weather Forecast
    W001(HttpStatus.BAD_GATEWAY, "W001", "날씨예보 조회에 실패했습니다."),

    // Chat
    CH001(HttpStatus.BAD_GATEWAY, "CH001", "챗 응답에 실패했습니다. 잠시 후 다시 시도해주세요."),
    CH002(HttpStatus.TOO_MANY_REQUESTS, "CH002", "AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요."),

    // Nutrient
    N001(HttpStatus.NOT_FOUND, "N001", "양액 레시피를 찾을 수 없습니다."),
    N002(HttpStatus.FORBIDDEN, "N002", "양액 레시피 수정/삭제 권한이 없습니다."),
    N003(HttpStatus.BAD_REQUEST, "N003", "배합할 수 없는 조합입니다."),

    // Rack (Zone·Rack·RackLevel 계층 — contract §4.10)
    R001(HttpStatus.NOT_FOUND, "R001", "존을 찾을 수 없습니다."),
    R002(HttpStatus.NOT_FOUND, "R002", "랙을 찾을 수 없습니다."),
    R003(HttpStatus.NOT_FOUND, "R003", "층을 찾을 수 없습니다."),
    R004(HttpStatus.CONFLICT, "R004", "랙 구조를 변경할 수 없습니다."),

    // Device (contract §4.10)
    E001(HttpStatus.NOT_FOUND, "E001", "장비를 찾을 수 없습니다."),
    E002(HttpStatus.CONFLICT, "E002", "이미 등록된 장비 시리얼입니다."),

    // Control (제어 도메인 — contract §4.12)
    CT001(HttpStatus.NOT_FOUND, "CT001", "제어 변경 항목을 찾을 수 없습니다."),
    CT002(HttpStatus.CONFLICT, "CT002", "통신이 두절된 장비는 조작할 수 없습니다."),
    CT003(HttpStatus.CONFLICT, "CT003", "현재 운전 모드에서 허용되지 않는 조작입니다."),
    CT004(HttpStatus.CONFLICT, "CT004", "적용 대기 큐 상한(존당 50건)을 초과했습니다."),
    CT005(HttpStatus.CONFLICT, "CT005", "대기 큐가 변경되었습니다. 최신 큐를 확인한 뒤 다시 적용해주세요."),

    // Alarm Event (알람 이벤트 도메인 — 이슈 #116)
    AL001(HttpStatus.NOT_FOUND, "AL001", "알람 이벤트를 찾을 수 없습니다."),
    AL002(HttpStatus.CONFLICT, "AL002", "현재 상태에서는 처리할 수 없는 알람입니다."),

    // Alarm Rule (알람 규칙 확장 — 이슈 #118)
    ALR001(HttpStatus.NOT_FOUND, "ALR001", "알람 규칙을 찾을 수 없습니다."),
    ALR002(HttpStatus.CONFLICT, "ALR002", "농장당 알람 규칙 상한을 초과했습니다."),
    ALR003(HttpStatus.BAD_REQUEST, "ALR003", "비교 조건과 임계값 구성이 올바르지 않습니다."),
    ALR004(HttpStatus.CONFLICT, "ALR004", "환경 임계치 설정에서 만들어진 규칙은 임계치 설정 API로만 변경할 수 있습니다."),

    // Saved Analysis (저장한 분석 — 이슈 #126)
    SA001(HttpStatus.NOT_FOUND, "SA001", "저장한 분석을 찾을 수 없습니다."),
    SA002(HttpStatus.CONFLICT, "SA002", "농장당 저장한 분석 개수 상한을 초과했습니다."),
    /** 수정(rename)·삭제 권한 없음 — 작성자 본인 또는 ADMIN만 가능(L002·N002와 동일 원칙). */
    SA004(HttpStatus.FORBIDDEN, "SA004", "저장한 분석 수정/삭제 권한이 없습니다."),
    /**
     * CSV 내보내기 행 수 상한 초과(이슈 #126) — {@code /readings/series}와 동일한 다운샘플 집계를
     * 재사용하므로 구조적으로 metric당 최대 버킷 수 × 4(MAX_SERIES_METRICS)를 넘을 수 없지만,
     * 방어선을 명시적으로 코드에 남긴다({@code ReadingService.MAX_EXPORT_ROWS} 참고).
     */
    SA003(HttpStatus.PAYLOAD_TOO_LARGE, "SA003", "내보내기 대상 데이터가 너무 많습니다. 기간·지표·스코프를 좁혀 다시 시도해주세요."),

    // Schedule (스케줄 골격 — 이슈 #129-C, 저장만 하고 실행하지 않는다)
    SCH001(HttpStatus.NOT_FOUND, "SCH001", "스케줄을 찾을 수 없습니다."),
    SCH002(HttpStatus.CONFLICT, "SCH002", "농장당 스케줄 개수 상한을 초과했습니다."),
    SCH003(HttpStatus.BAD_REQUEST, "SCH003", "cron 표현식 형식이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
