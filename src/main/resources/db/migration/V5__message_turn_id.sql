-- 메시지와 AI 서버 turn_id 연결 (인지 지표 POST 조인 키)

ALTER TABLE message ADD COLUMN turn_id VARCHAR(36);

CREATE INDEX idx_message_turn_id ON message (turn_id);
