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
     * 탈퇴 트랜잭션 직렬화용 users 행 잠금(SELECT ... FOR UPDATE — contract 탈퇴 봉쇄 ②).
     * 동시 탈퇴 2건은 승자 커밋 후 패자가 락 획득 시 @SQLRestriction 재평가로 빈 결과(A004)를
     * 받아 500 없이 수렴하고, 탈퇴↔멤버십 생성 TOCTOU도 이 락 + 가드/진입점 생존 검증으로 봉쇄된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
