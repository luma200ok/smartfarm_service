-- V6: diagnoses.image_path — 진단 이미지 원본 저장 경로(contract §3 Phase 3, 이슈 #20)
-- {farmId}/{diagnosisId}.{ext} 상대경로만 저장(base 디렉터리는 IMAGE_STORAGE_DIR env). 저장 실패 시 NULL 유지.
ALTER TABLE diagnoses ADD COLUMN image_path VARCHAR(512);
