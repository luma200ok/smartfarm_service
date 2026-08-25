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

    /**
     * 역할 변경(이슈 #122) — ADMIN이 {@code PATCH .../members/{memberId}/role}로 호출한다.
     * 초대 수락자({@link FarmRole#PENDING})의 승인도 이 전이로 처리한다.
     *
     * <p><b>"마지막 ADMIN을 강등할 수 없다"는 여기서 판정하지 않는다</b> — 농장 전체의 관리자 수를
     * 세야 하는 <b>농장 단위</b> 불변식이라 멤버십 한 행이 알 수 있는 사실이 아니다.
     * {@code FarmMemberService}가 농장 행을 잠근 뒤 검사한다(동시 강등 race 차단).
     */
    public void changeRole(FarmRole role) {
        this.role = role;
    }
}
