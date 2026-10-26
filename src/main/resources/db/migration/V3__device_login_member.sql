-- 기기 연결 코드로 로그인하는 노인 회원은 이메일·비밀번호 없이 생성된다

ALTER TABLE member ALTER COLUMN email DROP NOT NULL;
ALTER TABLE member ALTER COLUMN password DROP NOT NULL;
