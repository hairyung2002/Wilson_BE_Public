-- 보호자용 대화 요약 (api-spec 6-9·6-10·13, 결정 J2)
-- 대화가 끝난 뒤 AI 워커가 만들어 Kafka(wilson.conversation-summaries.v1)로 보낸다. 도착 전에는 NULL.

ALTER TABLE conversation ADD COLUMN summary VARCHAR(200);
