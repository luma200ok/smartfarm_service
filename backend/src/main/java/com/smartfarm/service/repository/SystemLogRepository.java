package com.smartfarm.service.repository;

import com.smartfarm.service.entity.SystemLog;
import com.smartfarm.service.entity.SystemLogCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    /** 목록 조회(카테고리 필터 없음) — 발생시각 내림차순, 동일 시각은 id 내림차순 안정 정렬. */
    Page<SystemLog> findByFarmIdOrderByOccurredAtDescIdDesc(Long farmId, Pageable pageable);

    /** 목록 조회(카테고리 필터 있음) — 위와 동일 정렬. */
    Page<SystemLog> findByFarmIdAndCategoryOrderByOccurredAtDescIdDesc(Long farmId, SystemLogCategory category,
                                                                        Pageable pageable);
}
