-- V20: alarm_rules — 알람 규칙 확장(이슈 #118, contract §4.6/§4.13 후속)
-- 배경: V10 farm_env_thresholds는 "농장당 1행 × indoor 온·습도 min/max 4컬럼"이라 프리뷰가 요구하는
-- 규칙(급액 EC 5분 지속 · 게이트웨이 무응답 3분 지속 · 랙/층 단위 스코프 · 경보/주의 등급 분화)을
-- 표현할 수 없었다. 이 마이그레이션이 농장당 N개 규칙 모델을 도입하고, 기존 4컬럼 설정을 규칙 행으로
-- 이관한다. 기존 마이그레이션(V1~V19)은 수정하지 않는다(시행된 마이그레이션 수정 금지 컨벤션).
--
-- ⚠️ farm_env_thresholds 테이블은 유지한다 — GET/PUT /env-thresholds(계약 §4.6)의 저장소로 계속
-- 쓰이며, 그 API가 저장될 때마다 EnvThresholdService가 대응 alarm_rules 행(threshold_id로 식별되는
-- "파생 규칙")을 제자리 upsert해 동기화한다. 평가 엔진은 alarm_rules만 본다.

CREATE TABLE alarm_rules (
    id               BIGSERIAL PRIMARY KEY,
    farm_id          BIGINT       NOT NULL REFERENCES farms (id),
    name             VARCHAR(50)  NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 지표 데이터 소스 라우팅: ENV_SNAPSHOT(V9 env_snapshots — farmId 없는 전역 단일 스트림) /
    -- SENSOR_READING(V15 sensor_readings — 농장>존>랙>층 스코프) / DEVICE_HEARTBEAT(장비 통신 두절).
    source           VARCHAR(20)  NOT NULL,
    -- source=ENV_SNAPSHOT이면 EnvMetric(INDOOR_TEMP·INDOOR_HUMIDITY), SENSOR_READING이면
    -- SensorMetric 7종 중 하나. DEVICE_HEARTBEAT은 지표 개념이 없어 null. 두 enum에 걸쳐 있어
    -- 문자열로 저장한다(alarm_events.metric_key 선례와 동일 원칙).
    metric           VARCHAR(20),
    comparator       VARCHAR(20)  NOT NULL,
    threshold_value  DOUBLE PRECISION,
    threshold_min    DOUBLE PRECISION,
    threshold_max    DOUBLE PRECISION,
    -- 지속시간(초) — 최초 이탈 시각부터 이 시간이 지나야 발동한다. 기존 "연속 2틱" 하드코딩을
    -- 이 값으로 일반화한다(이관값은 60 — 아래 이관 DML 주석 참고).
    duration_seconds INTEGER      NOT NULL,
    -- 규칙이 등급을 결정한다 — #116의 "severity 전부 WARNING 고정"을 여기서 해소한다.
    severity         VARCHAR(20)  NOT NULL,
    scope_type       VARCHAR(20)  NOT NULL,
    scope_id         BIGINT,
    -- 파생 규칙 표식(NULL이면 사용자가 alarm-rules API로 만든 일반 규칙) — GET/PUT /env-thresholds가
    -- 관리하는 규칙임을 뜻한다. 설정 행이 사라지면 파생 규칙도 함께 사라져야 하므로 CASCADE.
    threshold_id     BIGINT REFERENCES farm_env_thresholds (id) ON DELETE CASCADE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,

    -- FARM 스코프는 대상 id가 없고, 그 외 스코프는 반드시 있어야 한다.
    CONSTRAINT ck_alarm_rules_scope_id CHECK (
        (scope_type = 'FARM' AND scope_id IS NULL) OR (scope_type <> 'FARM' AND scope_id IS NOT NULL)
    ),
    -- env_snapshots는 farmId조차 없는 전역 단일 스트림이라 하위 스코프가 성립하지 않는다.
    CONSTRAINT ck_alarm_rules_env_scope CHECK (source <> 'ENV_SNAPSHOT' OR scope_type = 'FARM'),
    -- DEVICE_HEARTBEAT만 지표가 없고, DEVICE_HEARTBEAT만 ABSENT를 쓴다(양방향 강제).
    CONSTRAINT ck_alarm_rules_metric CHECK (
        (source = 'DEVICE_HEARTBEAT' AND metric IS NULL) OR (source <> 'DEVICE_HEARTBEAT' AND metric IS NOT NULL)
    ),
    CONSTRAINT ck_alarm_rules_absent CHECK ((comparator = 'ABSENT') = (source = 'DEVICE_HEARTBEAT')),
    -- comparator별로 필요한 threshold 컬럼이 채워져 있어야 한다(애플리케이션 검증 ALR003의 DB 방어선).
    CONSTRAINT ck_alarm_rules_comparator CHECK (
        (comparator IN ('GT', 'LT') AND threshold_value IS NOT NULL)
        OR (comparator = 'OUTSIDE_RANGE' AND threshold_min IS NOT NULL AND threshold_max IS NOT NULL
            AND threshold_min < threshold_max)
        OR (comparator = 'ABSENT')
    ),
    CONSTRAINT ck_alarm_rules_duration CHECK (duration_seconds > 0)
);

-- 평가 엔진의 주 경로(enabled=true 전량 조회) — EnvThresholdAlertService가 매 틱 실행한다.
CREATE INDEX ix_alarm_rules_farm_id_enabled ON alarm_rules (farm_id, enabled);

-- 파생 규칙은 (설정 행 × 지표 × 방향)당 정확히 1개여야 한다 — EnvThresholdService의 제자리 upsert가
-- 이 유일성에 기댄다(중복이 생기면 같은 설정이 두 개의 알람을 만든다).
CREATE UNIQUE INDEX ux_alarm_rules_derived ON alarm_rules (threshold_id, metric, comparator)
    WHERE threshold_id IS NOT NULL;

-- ── alarm_events 확장 ────────────────────────────────────────────────────────
-- rule_id: 발단 규칙. 규칙이 삭제돼도 과거 이벤트는 감사 이력으로 보존한다(threshold_id의 ON DELETE
-- SET NULL 선례와 동일 원칙 — 이벤트를 지우면 확인/처리 이력이 함께 사라져 감사 가치를 잃는다).
ALTER TABLE alarm_events ADD COLUMN rule_id BIGINT REFERENCES alarm_rules (id) ON DELETE SET NULL;
-- scope_type/scope_id: 프리뷰의 "군산1 · B3랙 4층" 위치 표기용. V19 이전 행은 null(=농장 단위).
ALTER TABLE alarm_events ADD COLUMN scope_type VARCHAR(20);
ALTER TABLE alarm_events ADD COLUMN scope_id BIGINT;

-- ── 기존 farm_env_thresholds 설정 이관 ───────────────────────────────────────
-- enabled=true인 설정의 값이 채워진 경계마다 규칙 1개씩(최대 4개). 하한=LT, 상한=GT.
-- duration_seconds=60 — 기존 동작을 그대로 보존한다. "연속 2틱"은 최초 이탈 틱(경과 0초)과
-- 다음 틱(경과 60초)에서 발동했으므로 실제 경과 시간은 틱 간격 1회분인 60초다(120초로 두면
-- 발동이 한 틱 늦어지는 행동 변경이 된다 — 이관은 동작 보존이 원칙).
INSERT INTO alarm_rules (farm_id, name, enabled, source, metric, comparator, threshold_value,
                         duration_seconds, severity, scope_type, scope_id, threshold_id,
                         created_at, updated_at)
SELECT t.farm_id, m.rule_name, TRUE, 'ENV_SNAPSHOT', m.metric, m.comparator, m.bound,
       60, 'WARNING', 'FARM', NULL, t.id, NOW(), NOW()
FROM farm_env_thresholds t
CROSS JOIN LATERAL (
    VALUES ('실내 온도 하한', 'INDOOR_TEMP', 'LT', t.indoor_temp_min),
           ('실내 온도 상한', 'INDOOR_TEMP', 'GT', t.indoor_temp_max),
           ('실내 습도 하한', 'INDOOR_HUMIDITY', 'LT', t.indoor_humidity_min),
           ('실내 습도 상한', 'INDOOR_HUMIDITY', 'GT', t.indoor_humidity_max)
) AS m(rule_name, metric, comparator, bound)
WHERE t.enabled = TRUE AND m.bound IS NOT NULL;

-- ⚠️ 미해결 알람 이벤트의 metric_key 재매핑 (이 문단을 빼면 조용한 유령 알람이 남는다)
-- #116의 metric_key는 "{EnvMetric}_{EnvDirection}"(예: INDOOR_TEMP_HIGH)였는데, #118부터는 규칙
-- 단위 키("RULE_{ruleId}")를 쓴다 — 스코프·임계값이 다른 두 규칙이 같은 키를 만들면 V19의 partial
-- unique index (farm_id, metric_key) WHERE status<>'RESOLVED'가 한쪽 알람을 조용히 삼키기 때문이다.
-- 키 형식이 바뀌면 이관 전에 열려 있던 이벤트는 새 평가 경로가 영원히 찾지 못해(자동 해소 불가)
-- 사용자가 수동 처리할 때까지 유령 알람으로 고착된다. 그래서 열린 이벤트만 새 키로 옮긴다.
-- (RESOLVED 이벤트는 감사 이력이고 unique index 대상도 아니라 옛 키 그대로 둔다.)
UPDATE alarm_events e
SET metric_key = 'RULE_' || r.id,
    rule_id    = r.id,
    scope_type = 'FARM'
FROM alarm_rules r
WHERE r.threshold_id IS NOT NULL
  AND r.farm_id = e.farm_id
  AND e.status <> 'RESOLVED'
  AND e.metric_key = r.metric || CASE r.comparator WHEN 'LT' THEN '_LOW' WHEN 'GT' THEN '_HIGH' END;
