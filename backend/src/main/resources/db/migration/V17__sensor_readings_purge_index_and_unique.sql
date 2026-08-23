-- V17: sensor_readings 퍼지 전용 인덱스 + 중복 적재 차단 unique(contract §4.11 사이클 2 리뷰 P3-2)
-- V15/V16은 수정하지 않는다(이미 시행된 마이그레이션 수정 금지 컨벤션) — 신규 변경은 신규 파일로.

-- purge 전용 단독 인덱스: 조회용 복합 인덱스 2종((farm_id, metric, measured_at desc),
-- (rack_level_id, metric, measured_at desc))은 선행 컬럼이 달라 SensorReadingPurgeScheduler의
-- `WHERE measured_at < :cutoff`에 쓸 수 없다(V9가 env_snapshots에 idx_env_snapshots_captured_at
-- 단독 인덱스를 둔 이유와 동일).
CREATE INDEX idx_sensor_readings_measured_at ON sensor_readings (measured_at);

-- 중복 적재 차단: 다중 인스턴스나 fixedDelay 드리프트로 같은 device·metric·분에 두 번 tick하면
-- findSeriesAggregated의 device_avg CTE가 그 중복을 오류 없이 조용히 평균에 흡수해 값이 왜곡된다.
CREATE UNIQUE INDEX ux_sensor_readings_device_metric_measured_at
    ON sensor_readings (device_id, metric, measured_at);
