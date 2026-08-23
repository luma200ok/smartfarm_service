package com.smartfarm.service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
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
