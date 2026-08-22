-- V8: 데모 계정 플래그 (이슈 #49, contract §4.5)
-- 파괴적 작업 서버측 차단(A007)·demo-login 대상 식별에 사용

ALTER TABLE users ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;
