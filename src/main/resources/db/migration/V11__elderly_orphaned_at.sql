-- 보호자 없이 남은 노인 표시 (api-spec 4-4, decisions.md B3·B5)
-- 마지막 보호자가 탈퇴하면 기록하고, 30일 안에 새 보호자가 연결되지 않으면 일별 배치가 5-5 정책으로 파기한다.

ALTER TABLE elderly ADD COLUMN orphaned_at TIMESTAMP;

CREATE INDEX idx_elderly_orphaned_at ON elderly (orphaned_at) WHERE orphaned_at IS NOT NULL;
