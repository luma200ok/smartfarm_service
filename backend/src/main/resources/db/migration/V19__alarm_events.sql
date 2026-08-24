-- V19: alarm_events, alarm_event_logs — 알람 이벤트 도메인(contract §4.6 후속, 이슈 #116)
-- 기존 임계치 웹훅(EnvThresholdAlertService, V10)은 브리치 감지 시 웹훅만 쏘고 이벤트를 저장하지
-- 않았다. 이번 마이그레이션으로 알람을 영속화하고 확인/처리 상태 전이·이력을 관리한다.
-- 기존 마이그레이션(V1~V18)은 수정하지 않는다(시행된 마이그레이션 수정 금지 컨벤션).

-- 알람 이벤트 — source_type은 ENV_THRESHOLD 1종뿐이지만 향후 소스 확장(장비 통신 두절 등)을
-- 대비해 enum 성격의 컬럼으로 둔다(handoff). metric_key는 소스별 의미가 달라 문자열로 저장하며,
-- ENV_THRESHOLD는 "{EnvMetric}_{EnvDirection}"(예: INDOOR_TEMP_HIGH) 형태로 farm×항목×방향
-- 조합을 표현한다 — 멱등성 판정(같은 조합 미해결 이벤트 존재 여부)의 키가 된다.
CREATE TABLE alarm_events (
    id              BIGSERIAL PRIMARY KEY,
    farm_id         BIGINT       NOT NULL REFERENCES farms (id),
    severity        VARCHAR(20)  NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,
    metric_key      VARCHAR(50)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    acknowledged_at TIMESTAMP,
    acknowledged_by BIGINT REFERENCES users (id),
    resolved_at     TIMESTAMP,
    resolved_by     BIGINT REFERENCES users (id),
    -- 임계치 설정이 삭제돼도(농장 설정 초기화 등) 과거 알람 이벤트는 감사 이력으로 보존한다
    -- (ON DELETE SET NULL — 이벤트 자체를 지우면 확인/처리 이력이 함께 사라져 감사 가치를 잃는다).
    threshold_id    BIGINT REFERENCES farm_env_thresholds (id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL,
    -- 낙관적 락(@Version) — acknowledge/resolve 동시 이중 처리 방지(상태 전이 가드와 별개 방어선).
    version         BIGINT       NOT NULL DEFAULT 0
);

-- 목록 조회(status·severity 필터) 주 경로.
CREATE INDEX ix_alarm_events_farm_id_status ON alarm_events (farm_id, status);
-- 목록 정렬(occurredAt 내림차순) 주 경로.
CREATE INDEX ix_alarm_events_farm_id_occurred_at ON alarm_events (farm_id, occurred_at);

-- 멱등성 DB 레벨 보증 — 같은 farm×metric_key 조합은 미해결(RESOLVED 아님) 상태로 동시에 2건 이상
-- 존재할 수 없다. 애플리케이션 레벨 조회 후 생성(findOpenEventByFarmAndMetric)이 1차 방어선이고,
-- 이 partial unique index가 2차 방어선이다(control_setpoints의 partial unique 선례와 동일 원칙).
CREATE UNIQUE INDEX ux_alarm_events_open_farm_metric ON alarm_events (farm_id, metric_key)
    WHERE status <> 'RESOLVED';

-- 알람 이벤트 타임라인 — 생성·확인·처리·메모 이력. actor_id는 시스템 처리(CREATED 최초 생성,
-- 자동 RESOLVED)일 때 null이다.
CREATE TABLE alarm_event_logs (
    id              BIGSERIAL PRIMARY KEY,
    alarm_event_id  BIGINT      NOT NULL REFERENCES alarm_events (id) ON DELETE CASCADE,
    action          VARCHAR(20) NOT NULL,
    actor_id        BIGINT REFERENCES users (id),
    note            VARCHAR(1000),
    created_at      TIMESTAMP   NOT NULL
);

-- 상세 조회의 타임라인 조회(alarm_event_id 스코프, 시간순) 주 경로.
CREATE INDEX ix_alarm_event_logs_alarm_event_id ON alarm_event_logs (alarm_event_id);
