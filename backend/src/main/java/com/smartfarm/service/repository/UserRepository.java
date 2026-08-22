package com.smartfarm.service.repository;

import com.smartfarm.service.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * demo-login 대상 조회(contract §4.5) — 시드가 1건을 보장하지만, 방어적으로
     * id 오름차순 첫 건 고정(결과 비결정성 제거). 미존재는 서버 결함으로 C002 처리.
     */
    Optional<User> findFirstByIsDemoTrueOrderByIdAsc();

    /** DemoAccountGuard 전용 경량 판정 — 엔티티 로드 없이 데모 계정 여부만 확인. */
    boolean existsByIdAndIsDemoTrue(Long id);

    /**
     * 탈퇴 트랜잭션 직렬화용 users 행 잠금(SELECT ... FOR UPDATE — contract 탈퇴 봉쇄 ②).
     * 동시 탈퇴 2건은 승자 커밋 후 패자가 락 획득 시 @SQLRestriction 재평가로 빈 결과(A004)를
     * 받아 500 없이 수렴하고, 탈퇴↔멤버십 생성 TOCTOU도 이 락 + 가드/진입점 생존 검증으로 봉쇄된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
