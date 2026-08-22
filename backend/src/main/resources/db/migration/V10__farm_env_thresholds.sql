-- V10: farm_env_thresholds — 농장별 환경 임계치 설정(contract §4.6, 이슈 #52)
-- farm당 1행(미설정 시 API는 enabled=false 기본값을 서비스 레벨에서 합성해 반환한다).
CREATE TABLE farm_env_thresholds (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    indoor_temp_min DOUBLE PRECISION,
    indoor_temp_max DOUBLE PRECISION,
    indoor_humidity_min DOUBLE PRECISION,
    indoor_humidity_max DOUBLE PRECISION,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_farm_env_thresholds_farm_id UNIQUE (farm_id)
);
