package com.smartfarm.service.service;

import com.smartfarm.service.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh_tokens 퍼지 배치 삭제 전용 — 스케줄러({@code RefreshTokenPurgeScheduler})가 배치
 * 단위로 반복 호출하는 <b>짧은 쓰기 트랜잭션</b> 하나만 갖는다(PrescriptionTransitionService
 * 선례). 별도 빈으로 분리한 이유: 스케줄러 메서드 안에서 배치 루프를 돌며 self-invocation으로
 * {@code @Transactional}을 걸면 프록시를 우회해 트랜잭션이 안 걸리는 함정이 있다 — 배치마다
 * 독립 트랜잭션(각각 커밋)이 되게 하려면 반드시 스케줄러 밖의 별도 빈이어야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenPurgeService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** 배치 1건 삭제 — 반환값이 batchSize보다 작으면(0 포함) 남은 대상이 없다는 신호. */
    @Transactional
    public int purgeBatch(LocalDateTime cutoff, int batchSize) {
        return refreshTokenRepository.deleteExpiredBeforeBatch(cutoff, batchSize);
    }
}
