package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진단 이력 — ai-server(/api/diagnoses) 프록시 결과를 농장 단위로 저장하는 기록(soft delete 없음).
 * 이미지 원본은 진단 생성 성공 후 별도로 저장되며(contract §3 Phase 3), 저장 성공 시에만
 * {@link #attachImage(String)}로 image_path가 채워진다(상태 전이 캡슐화).
 */
@Entity
@Table(name = "diagnoses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosisStatus status;

    @Column(length = 50)
    private String label;

    @Column(length = 50)
    private String labelKr;

    private Double prob;

    @Column(length = 50)
    private String part;

    @Column(length = 500)
    private String reason;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String camPngBase64;

    /** {farmId}/{diagnosisId}.{ext} 상대경로 — 저장 실패 시 null 유지(진단 자체는 실패시키지 않음). */
    @Column(length = 512)
    private String imagePath;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Diagnosis(Long farmId, Long createdBy, DiagnosisStatus status, String label, String labelKr,
                       Double prob, String part, String reason, String camPngBase64) {
        this.farmId = farmId;
        this.createdBy = createdBy;
        this.status = status;
        this.label = label;
        this.labelKr = labelKr;
        this.prob = prob;
        this.part = part;
        this.reason = reason;
        this.camPngBase64 = camPngBase64;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** 이미지 저장 성공 시에만 호출(ImageStorageService#store가 null을 반환하면 호출하지 않는다). */
    public void attachImage(String imagePath) {
        this.imagePath = imagePath;
    }
}
