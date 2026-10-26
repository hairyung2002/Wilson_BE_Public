-- 대화 유형·상태·Soft Delete 추가 (api-spec 6장)

ALTER TABLE conversation ADD COLUMN conversation_type VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE conversation ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'WAITING';
UPDATE conversation SET state = 'FINISHED' WHERE ended_at IS NOT NULL;
ALTER TABLE conversation ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_conversation_elderly_started_at ON conversation (elderly_id, started_at);
