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
