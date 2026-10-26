-- 보호자 알림 (api-spec 14장, 16-1, decisions.md E·H)
-- 알림이 어느 노인에 관한 것인지, 푸시를 보냈는지(밤사이 보류 포함)를 기록한다.
-- 기존 알림 종류(PROACTIVE_CARE, DEMENTIA_ALERT)는 운영 데이터가 없어 변환하지 않는다.

ALTER TABLE notification ADD COLUMN elderly_id BIGINT;
ALTER TABLE notification ADD CONSTRAINT fk_notification_elderly FOREIGN KEY (elderly_id) REFERENCES elderly (id);
ALTER TABLE notification ADD COLUMN push_status VARCHAR(15) NOT NULL DEFAULT 'SENT';

CREATE INDEX idx_notification_recipient_id ON notification (recipient_member_id, id);
CREATE INDEX idx_notification_deferred ON notification (push_status) WHERE push_status = 'DEFERRED';

-- 보호자별·노인별 알림 설정. null이면 역할 기본값(OWNER 받음, MEMBER 받지 않음)
ALTER TABLE care_relationship ADD COLUMN notify_pattern_change BOOLEAN;
ALTER TABLE care_relationship ADD COLUMN notify_proactive_no_response BOOLEAN;
ALTER TABLE care_relationship ADD COLUMN notify_daily_summary BOOLEAN;
