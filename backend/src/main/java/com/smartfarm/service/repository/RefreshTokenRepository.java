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
     * 퍼지 스캔 — 만료 후 유예 기간이 지난 행 삭제(RefreshTokenPurgeScheduler). 유예는 재사용
     * 감지 신호(만료 직후 탈취된 토큰의 재사용 시도) 보존 목적 — 즉시 삭제하면 그 신호를 잃는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
