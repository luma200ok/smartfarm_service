-- V12: chat_messages — AI 챗봇 이력(contract §4.7, 이슈 #54)
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    -- FK는 V3(diagnoses)·V4(prescriptions)·V11(farm_logs)와 동일 관례 — 고아 행 차단.
    farm_id BIGINT NOT NULL REFERENCES farms (id),
    created_by BIGINT NOT NULL REFERENCES users (id),
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    -- ai-server sources[] — Prescription.result와 동일하게 JSONB 배열로 저장.
    sources JSONB NOT NULL,
    fallback BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 목록 조회(GET .../chat)가 farmId 스코프 + 최신순 정렬이라 복합 인덱스로 커버.
CREATE INDEX idx_chat_messages_farm_id_created_at ON chat_messages (farm_id, created_at DESC, id DESC);
