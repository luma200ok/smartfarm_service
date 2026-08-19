package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 초대코드 — 만료(72h)까지 다인 재사용 가능 (contract §2).
 * 폐기 정책: 농장당 활성 코드 1건(재발급 시 기존 무효화), 멤버 제거 시 활성 코드 자동 무효화.
 * 코드 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 64, unique = true)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** 폐기 시각 — NULL이면 활성 */
    private LocalDateTime revokedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Invitation(Long farmId, String codeHash, LocalDateTime expiresAt) {
        this.farmId = farmId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
