-- V11: farm_logs — 작업일지(contract §4.8, 이슈 #56)
CREATE TABLE farm_logs (
    id BIGSERIAL PRIMARY KEY,
    -- FK는 V3(diagnoses)·V4(prescriptions)와 동일 관례 — 고아 행 차단.
    farm_id BIGINT NOT NULL REFERENCES farms (id),
    author BIGINT NOT NULL REFERENCES users (id),
    log_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    memo VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

-- 목록 조회(GET .../logs)가 farmId 스코프 + logDate 내림차순 정렬이라 복합 인덱스로 커버.
CREATE INDEX idx_farm_logs_farm_id_log_date ON farm_logs (farm_id, log_date DESC, id DESC);
