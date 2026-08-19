package com.smartfarm.service.scheduler;

import com.smartfarm.service.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh_tokens 퍼지 스케줄러 — 만료 후 {@value #GRACE_PERIOD_DAYS}일 유예가 지난 행을
 * 삭제한다(PRD §2.3 P1②). 유예 기간을 두는 이유: revoked=false인 채 막 만료된 토큰이
 * 재사용(탈취) 시도로 들어올 수 있는데, 즉시 삭제하면 그 재사용 감지 신호를 잃는다 —
 * 유예 구간 안에서는 행이 남아 있어 재사용 판정(AuthService의 revoke 여부 확인)이 가능하다.
 *
 * <p>PrescriptionSweeper 선례를 따른다: 주기는 프로퍼티화(ISO-8601 Duration, 운영 기본
 * 1일 1회)하고 application-test.yml에서 크게 키워 테스트 중 자동 발화를 차단한다(테스트는
 * {@link #purge()}를 직접 호출). 벌크 DELETE는 @Modifying(clearAutomatically·flushAutomatically
 * 둘 다 — #2에서 확립한 함정) — {@link RefreshTokenRepository#deleteExpiredBefore} 참고.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPurgeScheduler {

    static final long GRACE_PERIOD_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(initialDelayString = "${refresh-token.purge-initial-delay:P1D}",
            fixedDelayString = "${refresh-token.purge-fixed-delay:P1D}")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS);
        int deleted = refreshTokenRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("refresh_tokens 퍼지 — 만료 후 {}일 유예 경과 {}건 삭제", GRACE_PERIOD_DAYS, deleted);
        }
    }
}
