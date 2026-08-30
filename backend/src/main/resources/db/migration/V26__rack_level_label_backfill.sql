-- V26: rack_levels.label 백필 (이슈 #145)
-- 배경: contract §4.10·§4.11이 이 필드를 명시하는데 RackService#createLevels가 한 번도 채운 적이
-- 없어(이번 사이클에서 수정) 기존 행은 전부 label IS NULL이었다. 이 마이그레이션은 그 레거시 행을
-- 서비스 코드와 동일한 규칙("{levelNo}층")으로 채운다 — 신규 생성 층은 서비스 코드가 채우므로 이
-- 문장의 대상이 아니다(label IS NULL 조건이 자연히 걸러낸다).
-- ⚠️ 라벨 수정 API는 이번 범위 밖이다(#145 handoff) — 필요해지면 별도 이슈.

UPDATE rack_levels
SET label = level_no || '층'
WHERE label IS NULL;
