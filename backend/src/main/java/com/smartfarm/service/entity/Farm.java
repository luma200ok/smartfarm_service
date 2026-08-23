package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "farms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE farms SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CropType cropType;

    @Column(length = 255)
    private String location;

    /** 처방 완료/실패 디스코드 웹훅 URL(contract §3 Phase 3) — null=미설정. API 응답엔 원문 노출 금지. */
    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    /** 정식일(contract §4.10, V14) — 프리뷰 농장 카드의 "정식 N일" 표기용. 이번 사이클은 컬럼만
     * 추가하고 API 노출은 하지 않는다(§4.10 엔드포인트 표에 farm 엔드포인트 변경 없음 — 후속 사이클). */
    @Column(name = "planted_on")
    private LocalDate plantedOn;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private Farm(String name, CropType cropType, String location) {
        this.name = name;
        this.cropType = cropType;
        this.location = location;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * PATCH 부분 수정 — null 필드는 미변경 (contract §3: FarmRequest 부분).
     */
    public void update(String name, CropType cropType, String location) {
        if (name != null) {
            this.name = name;
        }
        if (cropType != null) {
            this.cropType = cropType;
        }
        if (location != null) {
            this.location = location;
        }
    }

    /**
     * 웹훅 URL 전체 교체 — PATCH `/webhook` 전용(contract §3: null=해제, 프리픽스 검증은 요청 DTO에서
     * 이미 끝난 값만 들어온다). {@link #update}의 "null=미변경" 부분 수정과 달리 여기는 null도 유효값
     * (해제)이라 별도 메서드로 분리한다 — 같은 시그니처로 묶으면 "null=미변경"과 "null=해제"가 충돌한다.
     */
    public void updateWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
}
