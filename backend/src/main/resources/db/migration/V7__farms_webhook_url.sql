-- V7: farms.webhook_url — 처방 완료/실패 디스코드 웹훅 알림(contract §3 Phase 3, 이슈 #21)
-- URL 원문은 API 응답에 노출하지 않는다(응답은 webhookConfigured boolean만). null=미설정/해제.
ALTER TABLE farms ADD COLUMN webhook_url VARCHAR(512);
