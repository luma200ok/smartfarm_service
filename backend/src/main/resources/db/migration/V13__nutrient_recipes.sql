-- V13: nutrient_recipes — 양액 배합 레시피(contract §4.9, 이슈 #64)
CREATE TABLE nutrient_recipes (
    id BIGSERIAL PRIMARY KEY,
    -- FK는 V3(diagnoses)·V4(prescriptions)·V11(farm_logs)·V12(chat_messages)와 동일 관례 — 고아 행 차단.
    farm_id BIGINT NOT NULL REFERENCES farms (id),
    author BIGINT NOT NULL REFERENCES users (id),
    name VARCHAR(50) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    -- 목표 ppm(mg/L), 0~1000 범위는 애플리케이션(Bean Validation)에서 강제.
    target_n DOUBLE PRECISION NOT NULL,
    target_p DOUBLE PRECISION NOT NULL,
    target_k DOUBLE PRECISION NOT NULL,
    target_ca DOUBLE PRECISION NOT NULL,
    target_mg DOUBLE PRECISION NOT NULL,
    target_s DOUBLE PRECISION NOT NULL,
    tank_volume_l DOUBLE PRECISION NOT NULL,
    concentration_factor DOUBLE PRECISION NOT NULL,
    -- 원수 분석값(선택) — 있으면 Ca/Mg 목표치에서 차감 보정.
    source_water_ca DOUBLE PRECISION,
    source_water_mg DOUBLE PRECISION,
    source_water_ec DOUBLE PRECISION,
    -- 저장 시점에 계산된 결과 스냅샷(JSONB) — chat_messages.sources(V12)와 동일 패턴. 이후 프리셋·
    -- 알고리즘이 바뀌어도 과거 레시피 조회 결과가 저장 당시와 달라지지 않도록 재계산 대신 스냅샷을 둔다
    -- (양액 투입량은 안전이 걸린 계산이라 이력 불변성이 재계산 편의보다 우선).
    calculation_snapshot JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 목록 조회(GET .../nutrient-recipes)가 farmId 스코프 + 최신순 정렬이라 복합 인덱스로 커버.
CREATE INDEX idx_nutrient_recipes_farm_id_created_at ON nutrient_recipes (farm_id, created_at DESC, id DESC);
