-- V25: schedules — 스케줄·자동화 규칙 골격(이슈 #129-C).
-- 프리뷰에 전용 화면이 없다(NAV_SECTIONS에 항목명만, mock.ts 주석이 "대표 화면 외 디자인 미정"이라고
-- 명시) — 이번 범위는 사용자 결정대로 데이터모델·CRUD 골격만이다.
--
-- ⚠️ 이 테이블은 "저장"만 한다 — @Scheduled 트리거·액션 실행 경로는 이 마이그레이션(과 대응
-- Schedule 엔티티)의 책임이 아니다. 디자인이 미확정인 상태에서 실행까지 만들면 나중에 버려진다.
-- 기존 마이그레이션(V1~V24, V23은 backend/128-pesticide가 선점)은 수정하지 않는다.
--
-- action_payload는 saved_analyses.metrics(V22)의 JSONB 관용구를 재사용한다 — action_type별로
-- 필요한 파라미터 형태가 달라(예: DEVICE_ON/OFF는 deviceId, SETPOINT_CHANGE는 metric+value) 골격
-- 단계에서 컬럼을 고정하지 않는다.

CREATE TABLE schedules (
    id              BIGSERIAL PRIMARY KEY,
    farm_id         BIGINT      NOT NULL REFERENCES farms (id),
    zone_id         BIGINT,
    name            VARCHAR(50) NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    cron_expression VARCHAR(100) NOT NULL,
    action_type     VARCHAR(20) NOT NULL,
    action_payload  JSONB,
    created_by      BIGINT      NOT NULL REFERENCES users (id),
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);

-- 목록 조회(GET, 농장별)의 주 경로.
CREATE INDEX ix_schedules_farm_id ON schedules (farm_id);
