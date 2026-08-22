-- V9: env_snapshots — 환경 시계열 적재(contract §4.6, 이슈 #52)
-- EnvironmentSnapshotPoller(60s)가 ai-server GET /api/environment/today 응답을 적재한다.
-- environment/today와 동일하게 전 농장 공용 단일 스트림(farm_id 없음) — 부분 응답 수용을 위해
-- captured_at을 제외한 전 필드 nullable.
CREATE TABLE env_snapshots (
    id BIGSERIAL PRIMARY KEY,
    captured_at TIMESTAMP NOT NULL,
    outdoor_temp DOUBLE PRECISION,
    outdoor_humidity DOUBLE PRECISION,
    indoor_temp DOUBLE PRECISION,
    indoor_humidity DOUBLE PRECISION,
    controlled BOOLEAN
);

-- range 조회(§4.6 24h/7d/30d)·90일 purge 스케줄러 모두 captured_at 범위 스캔이라 인덱스 필수.
CREATE INDEX idx_env_snapshots_captured_at ON env_snapshots (captured_at);
