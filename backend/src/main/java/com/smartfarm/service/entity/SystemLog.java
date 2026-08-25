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
 * 시스템 로그(V24, 이슈 #129-A) — <b>append-only</b>. 수정·삭제 API를 두지 않는다.
 *
 * <p>{@code actorId}는 사용자 조작 로그(제어 모드 변경·멤버 초대 발급·장비 등록/수정)에서는 그 조작을
 * 수행한 userId, 시스템 자동 이벤트(알람 이벤트 생성)에서는 null이다.
 *
 * <p>⚠️ <b>기록 실패가 원 작업을 깨뜨리면 안 된다</b>(#116 리뷰에서 확립된 "부가 작업은 원 작업과
 * 트랜잭션을 공유하지 않는다" 원칙) — 저장 지점은
 * {@link com.smartfarm.service.service.SystemLogService#record}가 유일하고, 그 메서드가
 * {@code REQUIRES_NEW} 전파 + 내부 예외 흡수로 호출측 트랜잭션과 완전히 격리한다.
 */
@Entity
@Table(name = "system_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemLogCategory category;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Builder
    private SystemLog(Long farmId, SystemLogCategory category, String message, Long actorId) {
        this.farmId = farmId;
        this.category = category;
        this.message = message;
        this.actorId = actorId;
    }

    @PrePersist
    void prePersist() {
        this.occurredAt = LocalDateTime.now();
    }
}
