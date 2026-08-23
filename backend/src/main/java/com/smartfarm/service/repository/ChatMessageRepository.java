package com.smartfarm.service.repository;

import com.smartfarm.service.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 목록 조회(contract §4.7) — 최신순, 동일 시각은 id 내림차순으로 안정 정렬(FarmLogRepository와 동일 패턴). */
    Page<ChatMessage> findByFarmIdOrderByCreatedAtDescIdDesc(Long farmId, Pageable pageable);
}
