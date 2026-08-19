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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 처방 비동기 job 이력 — POST 시 PENDING으로 저장되고 단일 워커가 상태를 전이시킨다(contract §3).
 * 상태 전이는 반드시 이 엔티티의 메서드로만 수행한다(불법 전이는 IllegalStateException — 상태 기계 캡슐화).
 *
 * <p>전이 규칙: PENDING → PROCESSING({@link #startProcessing()}) → COMPLETED({@link #complete})
 * / FAILED({@link #fail}). 종료 상태(COMPLETED/FAILED)에서는 어떤 전이도 불가.
 */
@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long createdBy;

    /** 참조 진단(선택) — 같은 farm 스코프 검증 후에만 저장된다(타 농장 진단 참조 → D001). */
    private Long diagnosisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status;

    @Column(nullable = false, length = 500)
    private String question;

    /** ai-server Prescription 구조화 JSON(summary/actions/caution/sources) — JSONB 저장(contract §4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String result;

    /** 실패 시 계약 ErrorCode 문자열(P002/P003) — 폴링 응답의 errorCode로 그대로 노출. */
    @Column(length = 10)
    private String errorCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 종료 시각 — COMPLETED/FAILED 공통으로 기록(폴링 클라이언트의 소요시간 표시·정렬용). */
    private LocalDateTime completedAt;

    @Builder
    private Prescription(Long farmId, Long createdBy, Long diagnosisId, String question) {
        this.farmId = farmId;
        this.createdBy = createdBy;
        this.diagnosisId = diagnosisId;
        this.question = question;
        // 초기 상태는 생성자에서 확정(PENDING) — @PrePersist 지연 대신 즉시 부여해
        // 저장 전 객체도 상태 기계 규칙(startProcessing 가드 등)을 그대로 따르게 한다.
        this.status = PrescriptionStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** PENDING → PROCESSING. 워커 픽업 시점에만 호출(중복 제출은 호출 전 상태 확인으로 스킵). */
    public void startProcessing() {
        if (this.status != PrescriptionStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 처리를 시작할 수 있습니다: " + this.status);
        }
        this.status = PrescriptionStatus.PROCESSING;
    }

    /** PROCESSING → COMPLETED. result JSON과 종료 시각을 함께 확정한다. */
    public void complete(String resultJson) {
        if (this.status != PrescriptionStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태에서만 완료할 수 있습니다: " + this.status);
        }
        this.status = PrescriptionStatus.COMPLETED;
        this.result = resultJson;
        this.completedAt = LocalDateTime.now();
    }

    /** PENDING/PROCESSING → FAILED. 종료 상태에서의 재실패는 불가(결과 덮어쓰기 차단). */
    public void fail(String errorCode) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException("종료 상태에서는 실패 전이가 불가합니다: " + this.status);
        }
        this.status = PrescriptionStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = LocalDateTime.now();
    }
}
