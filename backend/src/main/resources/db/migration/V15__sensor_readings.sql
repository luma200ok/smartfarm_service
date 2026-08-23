-- V15: sensor_readings — 센서 측정값(contract §4.11, 이슈 #90)
-- 가상 장비 시뮬레이터(60s tick)가 kind=SENSOR 장비마다 적재. env_snapshots(V9, farmId 없는 전역
-- 단일 하우스 실측)와 공존하는 별도 스트림 — 농장>존>랙>층 스코프, 7종 지표.
--
-- soft delete 컬럼을 두지 않는다(핸드오프 요건: 측정 이력은 하드 보존, 90일 경과분은 purge
-- 스케줄러가 하드 삭제). 위치 3종은 적재 시점 device 값의 스냅샷이라 device/zone/rack/rack_level이
-- 이후 soft delete(deleted_at만 설정)돼도 FK 대상 행 자체는 남아 있어 제약 위반이 없다.
CREATE TABLE sensor_readings (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT           NOT NULL REFERENCES farms (id),
    device_id     BIGINT           NOT NULL REFERENCES devices (id),
    zone_id       BIGINT REFERENCES zones (id),
    rack_id       BIGINT REFERENCES racks (id),
    rack_level_id BIGINT REFERENCES rack_levels (id),
    metric        VARCHAR(20)      NOT NULL,
    value         DOUBLE PRECISION NOT NULL,
    measured_at   TIMESTAMP        NOT NULL,
    source        VARCHAR(20)      NOT NULL,
    created_at    TIMESTAMP        NOT NULL
);

-- 조회 3종(series/latest/level-summary) + 90일 purge가 farm+metric+시간 범위 스캔이라 필수.
CREATE INDEX ix_sensor_readings_farm_metric_measured_at
    ON sensor_readings (farm_id, metric, measured_at DESC);

-- 랙 도면 셀(latest)·층별 비교(level-summary)의 rack_level_id 단위 조회 전용.
CREATE INDEX ix_sensor_readings_level_metric_measured_at
    ON sensor_readings (rack_level_id, metric, measured_at DESC);
