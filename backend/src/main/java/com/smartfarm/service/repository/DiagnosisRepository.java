package com.smartfarm.service.repository;

import com.smartfarm.service.dto.DiagnosisSummaryResponse;
import com.smartfarm.service.entity.Diagnosis;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    /** farm 스코프 필수 — diagnosisId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Diagnosis> findByIdAndFarmId(Long id, Long farmId);

    /**
     * farm 스코프 존재 검증 전용(BE #4 처방 — diagnosisId 참조 검증). 엔티티 로드 없이 exists만
     * 수행한다 — @Lob(camPngBase64) 로드는 활성 트랜잭션이 필요해 논트랜잭션 경로
     * (PrescriptionService.createPrescription, NOT_SUPPORTED)에서 findByIdAndFarmId를 쓰면
     * "Unable to access lob stream"으로 실패하고, 검증 목적엔 LOB 로드 자체가 낭비다.
     */
    boolean existsByIdAndFarmId(Long id, Long farmId);

    /**
     * 목록 전용 프로젝션 — @Lob camPngBase64(cam_png_base64)를 SELECT에서 제외해 페이지마다
     * 큰 base64 텍스트를 로드·폐기하는 낭비를 막는다(reviewer P2). countQuery는 프로젝션 select와
     * 별도로 필요(생성자 표현식에서는 count가 자동 유도되지 않음).
     */
    @Query(value = "SELECT new com.smartfarm.service.dto.DiagnosisSummaryResponse("
            + "d.id, d.status, d.label, d.labelKr, d.prob, d.part, d.createdBy, d.createdAt) "
            + "FROM Diagnosis d WHERE d.farmId = :farmId ORDER BY d.createdAt DESC, d.id DESC",
            countQuery = "SELECT COUNT(d) FROM Diagnosis d WHERE d.farmId = :farmId")
    Page<DiagnosisSummaryResponse> findSummariesByFarmId(@Param("farmId") Long farmId, Pageable pageable);
}
