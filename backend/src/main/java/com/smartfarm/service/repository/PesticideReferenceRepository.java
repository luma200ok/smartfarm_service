package com.smartfarm.service.repository;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideReference;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PesticideReferenceRepository extends JpaRepository<PesticideReference, Long> {

    /**
     * cropType 필수 + 병해충명 부분 검색(대소문자 무시). q가 빈 문자열이면 모든 문자열이 ""을
     * 포함하므로 자연히 "전체 조회"가 된다(handoff의 "q 생략 시 전체" 요구사항 — 별도 분기 불필요).
     * Pageable로 상한을 강제한다(#91 정책과 일관 — LocalPesticideReferenceProvider가 상한값을 넘김).
     */
    List<PesticideReference> findByCropTypeAndPestNameContainingIgnoreCaseOrderByPestNameAsc(
            CropType cropType, String pestNameQuery, Pageable pageable);
}
