-- V22: saved_analyses — 저장한 분석(이슈 #126) — 데이터 화면 필터(metrics/range/scope)를 이름
-- 붙여 저장하는 CRUD. 기존 마이그레이션(V1~V21)은 수정하지 않는다(시행된 마이그레이션 수정 금지
-- 컨벤션).
--
-- scope_type/scope_id는 V20 alarm_rules의 관용구를 그대로 따른다(AlarmScopeType과 값 집합 동일 —
-- FARM은 scope_id NULL, 그 외는 필수). metrics는 §4.11 SensorMetric 중 최대 4개를 담는 목록이라
-- ChatMessage#sources·NutrientRecipe#calculationSnapshot과 동일하게 JSONB 문자열 컬럼으로 둔다
-- (선택 근거는 SavedAnalysisService 클래스 주석 참고 — 최대 4개짜리 목록에 조인 테이블은 과하다).

CREATE TABLE saved_analyses (
    id         BIGSERIAL PRIMARY KEY,
    farm_id    BIGINT      NOT NULL REFERENCES farms (id),
    name       VARCHAR(50) NOT NULL,
    metrics    JSONB       NOT NULL,
    range      VARCHAR(10) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id   BIGINT,
    created_by BIGINT      NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,

    -- V20 alarm_rules의 ck_alarm_rules_scope_id와 동일한 규칙: FARM 스코프는 대상 id가 없고,
    -- 그 외 스코프는 반드시 있어야 한다.
    CONSTRAINT ck_saved_analyses_scope_id CHECK (
        (scope_type = 'FARM' AND scope_id IS NULL) OR (scope_type <> 'FARM' AND scope_id IS NOT NULL)
    )
);

-- 목록 조회(GET, 농장별 최신순)의 주 경로.
CREATE INDEX ix_saved_analyses_farm_id ON saved_analyses (farm_id);
