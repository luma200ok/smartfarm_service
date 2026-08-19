package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Invitation;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByCodeHash(String codeHash);

    /**
     * 농장의 활성 초대코드 전부 무효화 — 재발급 시(농장당 활성 1건)·멤버 제거 시(재합류 차단) 호출.
     * flushAutomatically: 같은 트랜잭션의 선행 변경(멤버십 delete 등)이 clear로 유실되지 않게
     * 벌크 UPDATE 전에 flush한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Invitation i SET i.revokedAt = :now WHERE i.farmId = :farmId AND i.revokedAt IS NULL")
    int revokeAllActiveByFarmId(@Param("farmId") Long farmId, @Param("now") LocalDateTime now);
}
