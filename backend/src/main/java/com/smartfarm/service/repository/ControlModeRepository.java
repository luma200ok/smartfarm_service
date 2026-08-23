package com.smartfarm.service.repository;

import com.smartfarm.service.entity.ControlMode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ControlModeRepository extends JpaRepository<ControlMode, Long> {

    /** farm 스코프 필수 — zoneId 단독 조회 금지(cross-tenant IDOR 차단). 조회 전용(락 없음). */
    Optional<ControlMode> findByZoneIdAndFarmId(Long zoneId, Long farmId);

    /** 비상 정지 — 농장 전 존의 모드 행(존 순서는 호출측이 잠금 순서와 맞춘다). */
    List<ControlMode> findByFarmId(Long farmId);

    /**
     * 존 단위 직렬화 잠금(SELECT ... FOR UPDATE — contract §4.12 동시성 3, #91 TOCTOU 교훈).
     * 같은 존의 모드 변경·큐 적재/취소·apply·비상 정지가 이 행 하나로 직렬화된다. 호출 전에
     * {@link #insertDefaultIfAbsent}로 행 존재를 보장해야 한다(미설정 존은 행이 없다).
     *
     * <p>farmId 조건을 함께 건다 — 잠금 지점이라도 테넌트 스코프는 예외가 아니다(호출측이 이미
     * 존 소속을 R001로 검증하지만, 레포 시그니처 자체로 강제해 우회 경로를 만들지 않는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cm FROM ControlMode cm WHERE cm.zoneId = :zoneId AND cm.farmId = :farmId")
    Optional<ControlMode> findByZoneIdForUpdate(@Param("zoneId") Long zoneId, @Param("farmId") Long farmId);

    /**
     * 잠금 지점(존당 1행) 지연 생성 — <b>{@code ON CONFLICT DO NOTHING}</b>이 핵심이다. 일반
     * INSERT로 경합하면 두 번째 트랜잭션이 unique 위반 예외를 받고 Postgres에서 그 트랜잭션 전체가
     * abort되어(이후 어떤 쿼리도 실행 불가) apply 트랜잭션 안에서 회복할 수 없다. ON CONFLICT는
     * 경합 시 상대 트랜잭션 종료를 기다렸다가 조용히 0행을 반환하므로 트랜잭션이 깨끗하게 유지된다.
     *
     * <p>{@code clearAutomatically}는 쓰지 않는다 — 비상 정지처럼 여러 존을 순회하며 이 메서드를
     * 반복 호출하는 경로에서 영속성 컨텍스트를 비우면 앞서 수정한 엔티티의 변경이 유실된다.
     * (호출측은 "모든 insert를 먼저, 그 다음 모든 lock" 순서를 지킨다.)
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO control_modes (farm_id, zone_id, mode, created_at) "
            + "VALUES (:farmId, :zoneId, 'AUTO', NOW()) ON CONFLICT (zone_id) DO NOTHING",
            nativeQuery = true)
    int insertDefaultIfAbsent(@Param("farmId") Long farmId, @Param("zoneId") Long zoneId);
}
