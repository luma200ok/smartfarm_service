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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 저장한 분석(V22, 이슈 #126) — 데이터 화면 필터(metrics/range/scope)를 이름 붙여 저장한 것.
 * <b>실행(조회 재적용)은 이 엔티티의 책임이 아니다</b> — FE가 저장된 필터값을 그대로
 * {@code GET /readings/series}의 파라미터로 재사용한다. 이 엔티티는 메타데이터만 CRUD한다.
 *
 * <p>soft delete 없음(FarmLog·NutrientRecipe와 동일 — 콘텐츠 저장물은 하드 삭제로 충분, handoff
 * 명시 없음 · 선례 재사용).
 *
 * <p>⚠️ <b>작성은 OPERATOR 이상</b>(이슈 #122 원칙, {@code FarmLogService} 선례 재사용) — VIEWER가
 * author가 되면 아래 삭제 규칙(author OR ADMIN)을 통해 삭제 권한까지 갖게 되어 "조회전용"이
 * 우회된다.
 *
 * <p>⚠️ <b>스코프 소멸과의 관계</b>(#118 선례 — {@code AlarmRule}이 스코프 소멸 시에도 행을 지우지
 * 않고 평가만 건너뛰는 것과 동일 원칙, {@code AlarmScopeResolver#exists} 참고): 이 행이 가리키는
 * zone/rack/level이 나중에 soft delete돼도 이 행 자체를 캐스케이드 삭제하지 않는다. 이 도메인에는
 * "실행" 경로가 없어(위 문단) 스코프 소멸이 조회 오류로 직접 이어지지 않기 때문이다 — 사라진
 * 스코프를 가리키는 저장 필터는 이름과 조건이 그대로 남은 <b>비활성 기록</b>으로 남는다. FE가 그
 * 조건으로 재조회를 시도하면 그 시점에 {@code GET /readings/series}가 자연히 404(R001~R003)로
 * 막으므로, 이 엔티티가 미리 캐스케이드 삭제·상태 플래그를 둘 필요가 없다.
 */
@Entity
@Table(name = "saved_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 선택 지표 목록(§4.11 SensorMetric 최대 4개) — JSON 배열 문자열(JSONB). ChatMessage#sources·
     * NutrientRecipe#calculationSnapshot과 동일 패턴(SavedAnalysisService가 ObjectMapper로 변환).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metrics;

    /** {@code EnvironmentHistoryRange.queryValue()} — "24h"|"7d"|"30d". */
    @Column(nullable = false, length = 10)
    private String range;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private AlarmScopeType scopeType;

    /** FARM 스코프면 null, 그 외는 zone/rack/rackLevel id(V22 CHECK 제약으로 DB에서도 강제). */
    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private SavedAnalysis(Long farmId, String name, String metrics, String range, AlarmScopeType scopeType,
                           Long scopeId, Long createdBy) {
        this.farmId = farmId;
        this.name = name;
        this.metrics = metrics;
        this.range = range;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * PATCH 부분 수정 — 이름(rename)만 허용한다. {@code metrics}/{@code range}/{@code scopeType}/
     * {@code scopeId}는 저장물의 정체성이라 PATCH 대상이 아니다(자세한 근거는
     * {@code SavedAnalysisUpdateRequest} 주석 — AlarmRuleUpdateRequest와 동일 원칙).
     */
    public void rename(String name) {
        this.name = name;
    }
}
