package com.smartfarm.service.exception;

import com.smartfarm.service.dto.ControlChangeResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CT005 전용 오류 본문(contract §4.12 동시성 1 "최신 큐를 응답에 실어 재확인시킨다") —
 * 표준 {@code {timestamp, code, message}}에 {@code pendingChanges}만 덧붙인 상위 호환 형태다.
 * 클라이언트는 이 목록을 그대로 화면에 반영해 재확인시킬 수 있다(별도 GET 왕복 불필요).
 */
public record ControlQueueConflictResponse(
        LocalDateTime timestamp,
        String code,
        String message,
        List<ControlChangeResponse> pendingChanges
) {

    public static ControlQueueConflictResponse of(ControlQueueConflictException e) {
        return new ControlQueueConflictResponse(LocalDateTime.now(), e.getErrorCode().getCode(),
                e.getMessage(), e.getPendingChanges());
    }
}
