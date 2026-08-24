package com.smartfarm.service.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CT005(대기 큐 낙관적 검증 실패)만 최신 큐를 함께 싣는다(contract §4.12 동시성 1). Spring은
     * 가장 구체적인 예외 타입의 핸들러를 고르므로, 상위 타입인 {@link CustomException} 핸들러보다
     * 이 핸들러가 우선 매치된다(선언 순서와 무관 — 타입 거리 기준).
     */
    @ExceptionHandler(ControlQueueConflictException.class)
    public ResponseEntity<ControlQueueConflictResponse> handleControlQueueConflict(
            ControlQueueConflictException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ControlQueueConflictResponse.of(e));
    }

    /**
     * JPA {@code @Version} 낙관적 락 충돌(예: AlarmEvent 동시 acknowledge/resolve, 이슈 #116) —
     * CustomException을 거치지 않고 Spring Data가 직접 던지므로 별도 핸들러가 없으면 마지막
     * {@code Exception.class} 핸들러로 떨어져 500 C002가 된다. 이 예외는 {@link CustomException}과
     * 상속 관계가 아니어서 우선순위 경쟁이 없다(타입이 겹치는 {@link ControlQueueConflictException}과
     * 달리 순서 무관하게 항상 이 핸들러가 매치된다).
     *
     * <p>⚠️ 이 핸들러는 <b>앱 전체</b>에 적용된다(이슈 #116 리뷰 P3) — 알람 이벤트 도메인에서
     * 처음 필요해졌을 뿐, {@code @Version}을 쓰는 어떤 엔티티든 낙관적 락 충돌 시 여기로 온다.
     * 그래서 응답 코드도 도메인 prefix(AL) 대신 공통 코드 {@link ErrorCode#C005}를 쓴다 — 예:
     * {@code Device}에 향후 {@code @Version}이 도입되면(Device 엔티티 클래스 주석 참고, 현재
     * {@code @DynamicUpdate} 컬럼 덮어쓰기 문제의 후속 과제로 명시돼 있음) 그 충돌도 이 핸들러가
     * 잡아 C005로 응답한다.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(ErrorCode.C005.getStatus())
                .body(ErrorResponse.of(ErrorCode.C005));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        // e.getMessage()는 단일 인자 생성자를 쓴 기존 전 도메인에서는 errorCode.getMessage()와
        // 항상 동일하다(CustomException 단일 인자 생성자가 super(errorCode.getMessage())를 호출) —
        // 상세 메시지 생성자(N003 등)를 쓴 경우에만 다른 값이 실린다.
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.C001.getMessage());
        return ResponseEntity.status(ErrorCode.C001.getStatus())
                .body(ErrorResponse.of(ErrorCode.C001, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class,
            // 필수 @RequestParam 누락(예: /api/nutrient-presets?cropType=… — 이슈 #64) — 이 핸들러가
            // 없으면 일반 Exception 핸들러로 떨어져 500 C002로 잘못 응답한다.
            MissingServletRequestParameterException.class,
            // @Validated 클래스의 @RequestParam 제약 위반(예: DeviceController#listDevices의
            // q @Size — 리뷰 P3 #89). MethodArgumentNotValidException과 달리 @RequestBody가 아닌
            // 파라미터 제약 위반이라 별도 예외 타입으로 온다 — 없으면 500 C002로 잘못 응답한다.
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        // 응답 본문은 C001 정형 메시지로 고정(정보 노출 방지 — 현행 유지). 다만 이 핸들러는
        // MissingServletRequestParameterException 등 클라이언트 실수뿐 아니라, 향후 @Validated
        // 서비스 빈에서 내부 호출자가 제약을 위반하는 서버측 프로그래밍 오류도 여기로 떨어질 수
        // 있어(리뷰 P3 #89) 서버 로그에만 원인을 남긴다.
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.C001.getStatus())
                .body(ErrorResponse.of(ErrorCode.C001));
    }

    /** 진단 업로드 — multipart file 파트 누락/용량 초과는 이미지 검증 실패와 동일하게 D002로 통일(handoff). */
    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ErrorResponse> handleMultipartError(Exception e) {
        return ResponseEntity.status(ErrorCode.D002.getStatus())
                .body(ErrorResponse.of(ErrorCode.D002));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.C003.getStatus())
                .body(ErrorResponse.of(ErrorCode.C003));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ErrorCode.C004.getStatus())
                .body(ErrorResponse.of(ErrorCode.C004));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.C002.getStatus())
                .body(ErrorResponse.of(ErrorCode.C002));
    }
}
