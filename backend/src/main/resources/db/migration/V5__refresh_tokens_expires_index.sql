-- V5: refresh_tokens 만료 스캔용 인덱스 (RefreshTokenPurgeScheduler 퍼지 스캔)
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
