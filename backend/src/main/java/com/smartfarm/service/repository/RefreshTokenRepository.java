package com.smartfarm.service.repository;

import com.smartfarm.service.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 활성 상태(revoked=false)인 경우에만 revoke 처리. 반환값 0이면 이미 revoke된 토큰
     * (동시 요청 race 포함) → 재사용으로 판정한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.id = :id AND rt.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /**
     * 퍼지 배치 삭제 — 만료 후 유예 기간이 지난 행을 배치 크기만큼씩 삭제한다
     * (RefreshTokenPurgeScheduler·RefreshTokenPurgeService). 유예는 재사용 감지 신호(만료
     * 직후 탈취된 토큰의 재사용 시도) 보존 목적 — 즉시 삭제하면 그 신호를 잃는다.
     *
     * <p>JPQL DELETE는 LIMIT을 지원하지 않아 네이티브 쿼리로 작성한다. 배치 단위로 나눠 반복
     * 호출하는 이유(리뷰 P2-1): 초회 백필·장기 중단 후 재기동처럼 대상 행이 대량으로 쌓인
     * 상태에서 단일 DELETE로 몰아 처리하면 장기 락 경합이 생길 수 있다 — 배치마다 별도
     * 트랜잭션(RefreshTokenPurgeService#purgeBatch)으로 커밋해 락 보유 시간을 짧게 쪼갠다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM refresh_tokens WHERE id IN "
            + "(SELECT id FROM refresh_tokens WHERE expires_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteExpiredBeforeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
