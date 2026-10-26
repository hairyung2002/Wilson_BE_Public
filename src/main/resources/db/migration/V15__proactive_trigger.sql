-- 선제 대화 (api-spec 6-2, 결정 C4~C6)
-- 대화를 누가 시작했는지(origin)와, 일정에 따라 노인 기기로 보낸 선제 대화 기록(proactive_trigger).

ALTER TABLE conversation ADD COLUMN origin VARCHAR(10) NOT NULL DEFAULT 'USER';

CREATE TABLE proactive_trigger (
    id                VARCHAR(36) PRIMARY KEY,
    elderly_id        BIGINT      NOT NULL,
    schedule_id       BIGINT,
    conversation_type VARCHAR(20) NOT NULL,
    label             VARCHAR(30),
    scheduled_at      TIMESTAMP   NOT NULL,
    status            VARCHAR(15) NOT NULL,
    sent_at           TIMESTAMP   NOT NULL,
    retried_at        TIMESTAMP,
    conversation_id   BIGINT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT fk_proactive_trigger_elderly FOREIGN KEY (elderly_id) REFERENCES elderly (id)
);

CREATE INDEX idx_proactive_trigger_waiting ON proactive_trigger (status, sent_at)
    WHERE status IN ('SENT', 'RETRIED');
CREATE INDEX idx_proactive_trigger_conversation ON proactive_trigger (conversation_id);
