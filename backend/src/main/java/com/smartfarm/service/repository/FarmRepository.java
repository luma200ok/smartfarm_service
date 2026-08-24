package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Farm;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    /**
     * 농장 단위 직렬화 잠금(SELECT ... FOR UPDATE — #91 TOCTOU 교훈, {@code ControlModeRepository}의
     * 존 단위 잠금과 같은 관용구).
     *
     * <p>농장당 리소스 <b>생성 상한</b>을 강제하는 쓰기 경로가 쓴다. 상한 판정이 "세어 보고 → 저장"인
     * check-then-act라, 잠금 없이는 병렬 요청이 전부 검사를 통과해 상한을 넘긴다. 알람 규칙 상한은
     * UX 제한이 아니라 <b>자원 방어선</b>이다 — 규칙 1개가 매 틱 조회 1~2개를 유발하고 스케줄러는 전
     * 농장의 규칙을 한 루프로 돌기 때문에, 한 농장의 초과가 다른 농장의 알람 지연으로 번진다.
     *
     * <p>{@code @SQLRestriction}이 적용되므로 soft delete된 농장은 여기서 빈 Optional이다.
     *
     * <p><b>데드락</b>: 이 잠금을 쓰는 경로는 농장 행 <b>하나</b>만 잡고 다른 잠금을 겹치지 않는다
     * (제어 도메인은 {@code control_modes} 행을 잡는 별개 경로다) — 순환 대기가 성립하지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Farm f WHERE f.id = :farmId")
    Optional<Farm> findByIdForUpdate(@Param("farmId") Long farmId);
}
