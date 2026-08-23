package com.smartfarm.service.repository;

import com.smartfarm.service.entity.NutrientRecipe;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientRecipeRepository extends JpaRepository<NutrientRecipe, Long> {

    /** 목록 조회(contract §4.9) — 최신순, 동일 시각은 id 내림차순으로 안정 정렬(FarmLogRepository와 동일 패턴). */
    Page<NutrientRecipe> findByFarmIdOrderByCreatedAtDescIdDesc(Long farmId, Pageable pageable);

    /** 농장 스코프 단건 조회 — 타 농장 id로 조회 시 cross-tenant 차단(FarmLogRepository와 동일 패턴). */
    Optional<NutrientRecipe> findByIdAndFarmId(Long id, Long farmId);
}
