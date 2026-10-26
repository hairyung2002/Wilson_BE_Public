-- 선제 대화 첫 안부 문장 (api-spec 6-2, 결정 J4)
-- 푸시 알림 본문·재푸시·대화 첫 발화(AI Speak)에 같은 문장을 쓰도록 트리거에 저장한다.
-- 기존 행은 30분 유효 기간이 지난 기록이라 빈 문자열로 채운다.

ALTER TABLE proactive_trigger ADD COLUMN greeting VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE proactive_trigger ALTER COLUMN greeting DROP DEFAULT;
