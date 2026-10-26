-- 보호자가 계정 없는 노인을 직접 등록할 수 있도록 elderly 확장
-- (노인 직접 가입은 member_id 유지, 보호자 등록은 member_id NULL)

ALTER TABLE elderly ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE elderly ADD COLUMN name VARCHAR(255);
UPDATE elderly e SET name = (SELECT m.name FROM member m WHERE m.id = e.member_id);
ALTER TABLE elderly ALTER COLUMN name SET NOT NULL;

ALTER TABLE elderly ADD COLUMN gender VARCHAR(10);
ALTER TABLE elderly ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
