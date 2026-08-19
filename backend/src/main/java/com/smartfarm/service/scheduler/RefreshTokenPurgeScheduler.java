package com.smartfarm.service.scheduler;

import com.smartfarm.service.service.RefreshTokenPurgeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * refresh_tokens 퍼지 스케줄러 — 만료 후 {@value #GRACE_PERIOD_DAYS}일 유예가 지난 행을
 * 삭제한다(PRD §2.3 P1②). 유예 기간을 두는 이유: revoked=false인 채 막 만료된 토큰이
 * 재사용(탈취) 시도로 들어올 수 있는데, 즉시 삭제하면 그 재사용 감지 신호를 잃는다 —
 * 유예 구간 안에서는 행이 남아 있어 재사용 판정(AuthService의 revoke 여부 확인)이 가능하다.
 *
 * <p>PrescriptionSweeper 선례를 따른다: 주기는 프로퍼티화(ISO-8601 Duration, 운영 기본
 * 1일 1회)하고 application-test.yml에서 크게 키워 테스트 중 자동 발화를 차단한다(테스트는
 * {@link #purge()}를 직접 호출).
 *
 * <p><b>배치 삭제(리뷰 P2-1)</b>: 대상이 대량으로 쌓인 상태(초회 백필·장기 중단 후 재기동)에서
 * 단일 DELETE로 몰아 처리하면 장기 락 경합이 생길 수 있어, 배치 크기만큼씩 나눠 반복 호출한다.
 * 각 배치는 {@link RefreshTokenPurgeService#purgeBatch}(별도 빈, 자체 짧은 트랜잭션)로 위임 —
 * self-invocation 프록시 함정을 피하면서 배치마다 독립적으로 커밋되게 한다. 안전 상한
 * {@value #MAX_BATCHES}배치를 넘기면 남은 대상은 다음 주기로 넘긴다(무제한 반복 방지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPurgeScheduler {

    static final long GRACE_PERIOD_DAYS = 7;
    static final int MAX_BATCHES = 20;

    private final RefreshTokenPurgeService refreshTokenPurgeService;

    @Value("${refresh-token.purge-batch-size:1000}")
    private int batchSize;

    @Scheduled(initialDelayString = "${refresh-token.purge-initial-delay:P1D}",
            fixedDelayString = "${refresh-token.purge-fixed-delay:P1D}")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS);
        int totalDeleted = 0;
        int batches = 0;
        int deleted;
        do {
            deleted = refreshTokenPurgeService.purgeBatch(cutoff, batchSize);
            totalDeleted += deleted;
            batches++;
        } while (deleted > 0 && batches < MAX_BATCHES);

        if (totalDeleted > 0) {
            log.info("refresh_tokens 퍼지 — 만료 후 {}일 유예 경과 {}건 삭제({}배치)",
                    GRACE_PERIOD_DAYS, totalDeleted, batches);
        }
    }
}
