-- V4: prescriptions — 처방 비동기 job 이력 (단일 워커 직렬 처리, PostgreSQL 16)
-- status: PENDING → PROCESSING → COMPLETED | FAILED (contract §3)
-- result: ai-server Prescription 구조화 JSON(summary/actions/caution/sources) — JSONB (contract §4)

CREATE TABLE prescriptions (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT       NOT NULL REFERENCES farms (id),
    created_by    BIGINT       NOT NULL REFERENCES users (id),
    diagnosis_id  BIGINT       REFERENCES diagnoses (id),
    status        VARCHAR(20)  NOT NULL,
    question      VARCHAR(500) NOT NULL,
    result        JSONB,
    error_code    VARCHAR(10),
    created_at    TIMESTAMP    NOT NULL,
    completed_at  TIMESTAMP
);

-- 농장별 최신순 목록 조회 최적화 (V3 diagnoses와 동일 패턴 — id DESC로 동시각 tie-break 안정 정렬)
CREATE INDEX ix_prescriptions_farm_id_created_at ON prescriptions (farm_id, created_at DESC, id DESC);

-- 재기동 복구 스캔용 partial index — 비종료 상태(PENDING/PROCESSING)만 인덱싱해
-- 이력이 쌓여도 복구 스캔 비용이 잔존 건 수에만 비례하게 한다
CREATE INDEX ix_prescriptions_active_status ON prescriptions (status, id) WHERE status IN ('PENDING', 'PROCESSING');
