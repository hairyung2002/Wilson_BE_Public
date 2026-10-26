-- AI 서버로 보내는 노인 가명 식별자 (AI 레포 docs/erd.md, research-retention.md)
-- AI의 target_user_id·RAG 개인 컨텍스트·인지지표가 노인 PK 대신 이 값으로 키잉된다

ALTER TABLE elderly ADD COLUMN subject_id UUID;
UPDATE elderly SET subject_id = gen_random_uuid() WHERE subject_id IS NULL;
ALTER TABLE elderly ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE elderly ADD CONSTRAINT uk_elderly_subject_id UNIQUE (subject_id);
