package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmEventLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventLogRepository extends JpaRepository<AlarmEventLog, Long> {

    /** 상세 조회 타임라인 — 오래된 순(발생 흐름 그대로), 동일 시각은 id 오름차순으로 안정 정렬. */
    List<AlarmEventLog> findByAlarmEventIdOrderByCreatedAtAscIdAsc(Long alarmEventId);
}
