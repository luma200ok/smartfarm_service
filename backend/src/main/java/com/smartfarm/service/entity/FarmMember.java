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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 농장 멤버십 — unique(farm_id, user_id)로 중복 합류 차단(동시 수락 race 포함).
 */
@Entity
@Table(name = "farm_members",
        uniqueConstraints = @UniqueConstraint(name = "ux_farm_members_farm_user",
                columnNames = {"farm_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FarmRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Builder
    private FarmMember(Long farmId, Long userId, FarmRole role) {
        this.farmId = farmId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        this.joinedAt = LocalDateTime.now();
    }
}
