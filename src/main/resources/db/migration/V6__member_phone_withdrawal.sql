-- 회원 전화번호, 탈퇴(Soft Delete) 추가 (api-spec 4장)
-- 탈퇴 시 이름·이메일·비밀번호·전화번호는 즉시 파기(익명화)한다

ALTER TABLE member ADD COLUMN phone VARCHAR(20);
ALTER TABLE member ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
