-- V16: device_metrics — 장비별 측정 지표 선언(contract §4.10 사이클 2, 이슈 #90)
-- kind=SENSOR는 1개 이상 선언(§4.11 SensorMetric 7종의 부분집합), CONTROLLER/GATEWAY는 비운다
-- (애플리케이션이 C001로 강제 — 계층 무관 독립 사실이라 CHECK 제약보다 서비스 계층이 자연스럽다).
-- V15는 sensor_readings가 선점해 V16으로 번호를 매긴다.
CREATE TABLE device_metrics (
    device_id BIGINT      NOT NULL REFERENCES devices (id),
    metric    VARCHAR(20) NOT NULL,
    PRIMARY KEY (device_id, metric)
);
