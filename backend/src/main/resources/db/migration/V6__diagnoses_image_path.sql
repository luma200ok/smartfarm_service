-- V6: diagnoses.image_path — 진단 이미지 원본 저장 경로(contract §3 Phase 3, 이슈 #20)
-- {farmId}/{diagnosisId}.{ext} 상대경로만 저장(base 디렉터리는 IMAGE_STORAGE_DIR env). 저장 실패 시 NULL 유지.
ALTER TABLE diagnoses ADD COLUMN image_path VARCHAR(512);

-- 업로드 시 화이트리스트 검증을 통과한 원 content-type을 그대로 기록(조회 스트리밍이 확장자 역산 대신
-- 이 값을 쓰게 해 저장 시점 타입과 응답 Content-Type의 불일치 가능성을 원천 제거 — reviewer P2).
ALTER TABLE diagnoses ADD COLUMN image_content_type VARCHAR(64);
