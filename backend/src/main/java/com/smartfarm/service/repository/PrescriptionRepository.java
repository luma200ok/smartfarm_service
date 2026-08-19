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
