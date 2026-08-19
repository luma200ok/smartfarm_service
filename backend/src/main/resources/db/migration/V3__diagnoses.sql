-- V3: diagnoses — 진단 이력 (ai-server 프록시 결과, PostgreSQL 16)
-- 이미지 원본은 저장하지 않는다(contract §6) — Grad-CAM 오버레이(cam_png_base64)만 보관.

CREATE TABLE diagnoses (
    id              BIGSERIAL PRIMARY KEY,
    farm_id         BIGINT       NOT NULL REFERENCES farms (id),
    created_by      BIGINT       NOT NULL REFERENCES users (id),
    status          VARCHAR(20)  NOT NULL,
    label           VARCHAR(50),
    label_kr        VARCHAR(50),
    prob            DOUBLE PRECISION,
    part            VARCHAR(50),
    reason          VARCHAR(500),
    cam_png_base64  TEXT,
    created_at      TIMESTAMP    NOT NULL
);

-- 농장별 최신순 목록 조회 최적화 (id DESC로 동시각 tie-break 안정 정렬)
CREATE INDEX ix_diagnoses_farm_id_created_at ON diagnoses (farm_id, created_at DESC, id DESC);
