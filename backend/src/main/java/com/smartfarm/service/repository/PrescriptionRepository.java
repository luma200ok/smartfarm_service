package com.smartfarm.service.repository;

import com.smartfarm.service.dto.PrescriptionSummaryResponse;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** farm 스코프 필수 — prescriptionId 단독 조회 금지(cross-tenant IDOR 차단, DiagnosisRepository 선례) */
    Optional<Prescription> findByIdAndFarmId(Long id, Long farmId);

    /**
     * 목록 전용 프로젝션 — JSONB result 본문을 SELECT에서 제외해 페이지마다 큰 결과 JSON을
     * 로드·폐기하는 낭비를 막는다(DiagnosisRepository 선례). countQuery는 별도 필요.
     */
    @Query(value = "SELECT new com.smartfarm.service.dto.PrescriptionSummaryResponse("
            + "p.id, p.status, p.question, p.createdBy, p.createdAt, p.completedAt) "
            + "FROM Prescription p WHERE p.farmId = :farmId ORDER BY p.createdAt DESC, p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Prescription p WHERE p.farmId = :farmId")
    Page<PrescriptionSummaryResponse> findSummariesByFarmId(@Param("farmId") Long farmId, Pageable pageable);

    /** 접수 상한(contract §3) — 농장당 진행 중(PENDING+PROCESSING) 건수. partial index 스캔. */
    long countByFarmIdAndStatusIn(Long farmId, Collection<PrescriptionStatus> statuses);

    /** 재기동 복구 — PENDING 잔존 건 재큐잉용(id 오름차순 = 접수 순서 유지, partial index 스캔). */
    @Query("SELECT p.id FROM Prescription p WHERE p.status = :status ORDER BY p.id ASC")
    List<Long> findIdsByStatus(@Param("status") PrescriptionStatus status);

    /**
     * 워커 픽업 — PENDING일 때만 PROCESSING으로 전이하는 조건부 UPDATE(반환 0 = 픽업 실패 → 스킵).
     * 단일 워커 전제와 무관하게 "PENDING에서만 픽업" 불변식을 DB 수준에서 강제한다(#2 패턴:
     * flushAutomatically로 벌크 연산 전 영속성 컨텍스트 flush, clearAutomatically로 이후 조회 최신화).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Prescription p SET p.status = :processing WHERE p.id = :id AND p.status = :pending")
    int claimForProcessing(@Param("id") Long id,
                           @Param("pending") PrescriptionStatus pending,
                           @Param("processing") PrescriptionStatus processing);

    /** 스위퍼 재큐잉 후보 — 연령 컷오프 + Pageable LIMIT(무제한 스캔 방지, contract §3). */
    @Query("SELECT p.id FROM Prescription p WHERE p.status = :status AND p.createdAt < :cutoff ORDER BY p.id ASC")
    List<Long> findIdsByStatusAndCreatedAtBefore(@Param("status") PrescriptionStatus status,
                                                 @Param("cutoff") LocalDateTime cutoff,
                                                 Pageable pageable);

    /**
     * 스위퍼 연령 컷오프 일괄 실패 처리 — 상태 조건이 WHERE에 있어 원자적(선별-후-갱신 레이스 없음):
     * 컷오프 스캔과 워커 픽업(claimForProcessing 성격의 상태 전이)이 겹쳐도 이미 전이된 행은
     * status 불일치로 제외된다. excludeId = 워커가 지금 처리 중인 in-flight id(없으면 -1) —
     * 큐 대기가 길었던 job이 createdAt 기준 연령으로 오판되는 것을 차단(단일 워커라 in-flight는 최대 1건).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Prescription p SET p.status = :failed, p.errorCode = :errorCode, p.completedAt = :now "
            + "WHERE p.status = :status AND p.createdAt < :cutoff AND p.id <> :excludeId")
    int failStaleByStatusBefore(@Param("status") PrescriptionStatus status,
                                @Param("cutoff") LocalDateTime cutoff,
                                @Param("excludeId") Long excludeId,
                                @Param("failed") PrescriptionStatus failed,
                                @Param("errorCode") String errorCode,
                                @Param("now") LocalDateTime now);

    /**
     * 재기동 복구 — 이전 프로세스에서 PROCESSING으로 남은 건 일괄 FAILED(P002) 처리.
     * ai-server 호출이 어디까지 진행됐는지 알 수 없으므로 보수적으로 실패 처리하고 사용자가
     * 재요청하게 한다(중복 처방 생성 방지 — LLM 호출은 멱등이 아님). 단일 UPDATE로 처리해
     * 잔존 건수와 무관하게 복구가 즉시 끝나게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Prescription p SET p.status = :failed, p.errorCode = :errorCode, p.completedAt = :now "
            + "WHERE p.status = :processing")
    int failAllProcessing(@Param("processing") PrescriptionStatus processing,
                          @Param("failed") PrescriptionStatus failed,
                          @Param("errorCode") String errorCode,
                          @Param("now") LocalDateTime now);
}
