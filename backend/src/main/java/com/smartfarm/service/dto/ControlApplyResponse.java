package com.smartfarm.service.dto;

import java.time.LocalDateTime;

/**
 * 대기 큐 일괄 적용 결과(contract §4.12 — POST /control/apply). 적용 직후의 존 제어 상태를 함께
 * 실어 클라이언트가 재조회 없이 화면을 갱신할 수 있게 한다(적용은 단일 트랜잭션이므로 이 state는
 * 커밋될 최종 상태와 일치한다).
 *
 * <p>{@code skippedCount}는 적용 시점에 대상이 사라진(존·장비 삭제 등) 항목 수다 — 캐스케이드가
 * 정상 동작하면 0이며, 그 항목은 적용되지 않고 DISCARDED로 정리된다(방어적 경로).
 */
public record ControlApplyResponse(
        Long zoneId,
        int appliedCount,
        int skippedCount,
        LocalDateTime appliedAt,
        boolean simulated,
        ControlStateResponse state
) {
}
