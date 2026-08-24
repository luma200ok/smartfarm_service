package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알람 이벤트 타임라인 항목(V19, 이슈 #116) — 상세 조회에서 시간순으로 노출한다. {@code actorId}는
 * 시스템 처리(최초 CREATED, 정상 복귀 자동 RESOLVED)일 때 null이다. 부모 {@link AlarmEvent} 삭제 시
 * ON DELETE CASCADE로 함께 제거된다(V19) — 다만 이 프로젝트는 AlarmEvent를 하드 삭제하는 경로를
 * 두지 않으므로(감사 이력 보존) 실질적으로 발동하지 않는 안전망이다.
 */
@Entity
@Table(name = "alarm_event_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_event_id", nullable = false)
    private Long alarmEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmEventLogAction action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AlarmEventLog(Long alarmEventId, AlarmEventLogAction action, Long actorId, String note) {
        this.alarmEventId = alarmEventId;
        this.action = action;
        this.actorId = actorId;
        this.note = note;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** {@link AlarmEvent} id + action(+actor/note)로 로그 행을 만드는 편의 팩토리. */
    public static AlarmEventLog of(AlarmEvent event, AlarmEventLogAction action, Long actorId, String note) {
        return AlarmEventLog.builder()
                .alarmEventId(event.getId())
                .action(action)
                .actorId(actorId)
                .note(note)
                .build();
    }
}
