-- V16: device_metrics — 장비별 측정 지표 선언(contract §4.10 사이클 2, 이슈 #90)
-- kind=SENSOR는 1개 이상 선언(§4.11 SensorMetric 7종의 부분집합), CONTROLLER/GATEWAY는 비운다
-- (애플리케이션이 C001로 강제 — 계층 무관 독립 사실이라 CHECK 제약보다 서비스 계층이 자연스럽다).
-- V15는 sensor_readings가 선점해 V16으로 번호를 매긴다.
CREATE TABLE device_metrics (
    device_id BIGINT      NOT NULL REFERENCES devices (id),
    metric    VARCHAR(20) NOT NULL,
    PRIMARY KEY (device_id, metric)
);

-- 백필(사이클 2 리뷰 P2-1): #89가 이미 main에 배포됐으므로 이 마이그레이션이 적용되는 시점에
-- kind='SENSOR'인 기존 장비가 있을 수 있다. 없으면 자연히 no-op이라 운영 상태와 무관하게 안전하다.
-- 백필 없이 두면 device_metrics가 빈 채로 시작해 updateDevice의 null=미변경 병합이 빈 Set을
-- 재검증해 C001로 거부하고(이름 하나 고치는 PATCH까지 막힘), 시뮬레이터도 에러 없이 조용히
-- 0행 생성한다(사용자에겐 "센서 무반응"으로만 보임). 기본 지표는 TEMPERATURE로 둔다.
INSERT INTO device_metrics (device_id, metric)
SELECT id, 'TEMPERATURE' FROM devices WHERE kind = 'SENSOR';
