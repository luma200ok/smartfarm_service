-- V24: system_logs — 시스템 로그(이슈 #129-A) — append-only 이벤트 로그.
-- 사용자 조작 로그(제어 모드 변경·멤버 초대 발급·장비 등록/수정)와 시스템 자동 이벤트(알람 이벤트
-- 생성)를 함께 기록한다. 기록 지점은 handoff대로 이미 존재하는 4곳으로 제한한다 — 모든 서비스에
-- 로깅을 뿌리면 범위가 폭발한다(펌웨어 배포·리포트 생성 등 아직 없는 기능은 제외). 기존 마이그레이션
-- (V1~V23, V23은 backend/128-pesticide가 선점)은 수정하지 않는다.
--
-- actor_id는 시스템 자동 이벤트(알람 이벤트 생성)에서 null이다 — farm_logs.author와 동일하게 FK 없는
-- plain 값 컬럼으로 둔다(감사 로그는 행위자가 탈퇴해도 기록 자체는 남아야 한다).

CREATE TABLE system_logs (
    id          BIGSERIAL PRIMARY KEY,
    farm_id     BIGINT       NOT NULL REFERENCES farms (id),
    category    VARCHAR(20)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    actor_id    BIGINT,
    occurred_at TIMESTAMP    NOT NULL
);

-- 목록 조회(GET, farm별 최신순 + category 필터)의 주 경로.
CREATE INDEX ix_system_logs_farm_id_occurred_at ON system_logs (farm_id, occurred_at DESC);
