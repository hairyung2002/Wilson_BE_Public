-- 보호자 역할 OWNER/MEMBER (api-spec 5장, decisions.md B3)
-- 기존 is_primary를 role로 바꾼다. 노인마다 OWNER는 1명:
--   주보호자 표시가 있는 연결 중 가장 먼저 만든 것, 없으면 그 노인의 가장 먼저 만든 연결을 OWNER로 한다.

ALTER TABLE care_relationship ADD COLUMN role VARCHAR(10);

UPDATE care_relationship SET role = 'MEMBER';

UPDATE care_relationship cr SET role = 'OWNER'
WHERE cr.id = (
    SELECT COALESCE(
        (SELECT MIN(p.id) FROM care_relationship p WHERE p.elderly_id = cr.elderly_id AND p.is_primary = TRUE),
        (SELECT MIN(a.id) FROM care_relationship a WHERE a.elderly_id = cr.elderly_id)
    )
);

ALTER TABLE care_relationship ALTER COLUMN role SET NOT NULL;
ALTER TABLE care_relationship DROP COLUMN is_primary;

-- 노인당 OWNER 1명을 DB에서도 보장한다 (주보호자 넘기기는 기존 OWNER를 먼저 내린 뒤 올린다)
CREATE UNIQUE INDEX uk_care_relationship_owner ON care_relationship (elderly_id) WHERE role = 'OWNER';
