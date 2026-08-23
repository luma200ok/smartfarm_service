package com.smartfarm.service.exception;

import com.smartfarm.service.dto.ControlChangeResponse;
import java.util.List;
import lombok.Getter;

/**
 * 적용 대기 큐 낙관적 검증 실패(CT005 — contract §4.12 동시성 1). 계약이 "<b>최신 큐를 응답에 실어</b>
 * 재확인시킨다"를 요구하므로, 기존 {@code {timestamp, code, message}} 형식만으로는 부족해
 * {@link CustomException}을 상속한 전용 예외로 최신 PENDING 목록을 함께 실어 보낸다
 * (예외 = {@code CustomException(ErrorCode)} 단일 패턴은 유지 — 상속형이라 기존 핸들러도 매치된다).
 *
 * <p>응답 본문은 {@code ControlQueueConflictResponse}로, 기존 오류 3필드에
 * {@code pendingChanges}만 추가한 상위 호환 형태다({@code GlobalExceptionHandler} 참고).
 */
@Getter
public class ControlQueueConflictException extends CustomException {

    private final transient List<ControlChangeResponse> pendingChanges;

    public ControlQueueConflictException(List<ControlChangeResponse> pendingChanges) {
        super(ErrorCode.CT005);
        this.pendingChanges = List.copyOf(pendingChanges);
    }
}
