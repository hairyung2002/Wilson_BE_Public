# api-spec.md

# Wilson Backend API Specification

Version : v1.2

Author : Wilson Backend Team

Last Update : 2026-10-22

---

# 0. 구현 현황 및 명세 변경점 (v1.1 · v1.2)

> 이 장은 v1.0 명세와 실제 구현의 차이, 그리고 v1.2 결정 사항을 정리한다. 본문에는 `⚠️ v1.1 변경` / `📝 v1.2` 표시를 달았다.
> 결정 배경은 `docs/decisions.md`, AI 연동 계약은 `docs/ai-integration-contract.md`(BE 확정, 결정 J)를 본다.
> AI 서버 연동은 AI 레포(`wilson_chatbot`)의 `proto/`·`docs/`를 기준으로 한다. 대화 언어는 `ko-KR`이다.
> AI에는 노인 PK 대신 가명 식별자(`elderly.subject_id`, UUID)만 보낸다.

## 0-1. API 구현 현황

상태: ✅ 구현 · 📝 v1.2 설계(구현 전) · ⏳ AI 팀 작업 대기 · 🗑️ 폐기

| 명세 | 엔드포인트 | 상태 | 비고 |
|---|---|---|---|
| 3-1 회원가입 | `POST /api/v1/members` | ✅ | 노인 본인 가입 시 동의(5-12) 필수 |
| 3-2 로그인 | `POST /api/v1/auth/login` | ✅ | |
| 3-3 토큰 재발급 | `POST /api/v1/auth/reissue` | ✅ | Refresh Token을 요청 바디로 받고 새로 발급(회전) |
| 3-4 로그아웃 | `POST /api/v1/auth/logout` | ✅ | 회원의 모든 토큰 무효화, 푸시 토큰 삭제 |
| 3-5 기기 로그인 | `POST /api/v1/auth/device-login` | ✅ | 보호자가 발급한 6자리 코드로 노인 기기 로그인 |
| 4-1 내 정보 조회 | `GET /api/v1/users/me` | ✅ | 노인 회원이면 `elderlyId` 포함 |
| 4-2·4-3 수정·비밀번호 변경 | `PATCH /api/v1/users/me`, `/password` | ✅ | 보호자만 |
| 4-4 회원 탈퇴 | `DELETE /api/v1/users/me` | ✅ | OWNER인 노인에 다른 보호자가 있으면 `409 G005`. 보호자 없이 남은 노인은 30일 뒤 파기(매일 04:00 배치, V11) |
| 4-5 푸시 토큰 등록 | `PUT /api/v1/users/me/push-token` | ✅ | 노인·보호자 공통, 회원당 1개, 다른 회원의 같은 기기 토큰은 이전 (V12) |
| 5-1~5-4 노인 등록·조회·수정 | `/api/v1/elderlies` | ✅ | 등록 시 동의 필수(대리, 법적 근거), 등록자는 OWNER |
| 5-5 노인 삭제 | `DELETE /api/v1/elderlies/{id}` | ✅ · ⏳ | OWNER만. 실명·원문 파기, 지표는 연구 보관 동의 시 가명 보관. AI 임베딩 파기는 Kafka `subject-erasure` 요청 — outbox에 남겨 1분마다 재발행 (J3 ✅, AI 워커 ⏳) |
| 5-6 기기 연결 코드 | `POST /api/v1/elderlies/{id}/device-code` | ✅ | |
| 5-7 보호자 초대 코드 | `POST /api/v1/elderlies/{id}/invite-code` | ✅ | 노인별 1개, 24시간, 1회용 |
| 5-8 초대 코드로 연결 | `POST /api/v1/care-relationships` | ✅ | `elderlyId` 방식 폐기, `inviteCode`만 |
| 5-9~5-11 보호자 목록·주보호자 넘기기·연결 해제 | `/api/v1/elderlies/{id}/caregivers` 등 | ✅ | `role` OWNER/MEMBER (V9) |
| 5-12 동의 | `/api/v1/elderlies/{id}/consents` | ✅ | 이력으로 쌓음 (V10) |
| 6-1 대화 시작 | `POST /api/v1/conversations` | ✅ | 노인 기기 토큰 전용 |
| 6-2 선제적 대화 | `POST /api/v1/conversations/proactive` | ✅ · ⏳ | 일정 → FCM 푸시 → 앱이 시작, 10분 재푸시·무응답 알림 (V15). 첫 안부는 BE 문장을 푸시에 표시하고 AI `Speak`로 읽음 (J4 ✅, V18). AI `Speak` 전까지는 텍스트만 보내 기기 음성으로 읽음 ⏳ |
| 6-3~6-7 음성·STT·AI·TTS | `WS /ws/v1/conversations/{id}/voice` | ✅ | 🗑️ HTTP 콜백 5종 → WebSocket + gRPC 스트리밍 |
| 6-8 대화 종료 | `PATCH /api/v1/conversations/{id}/finish` | ✅ · ⏳ | 종료(자동 종료 포함)가 커밋되면 Kafka `conversation-ended` 발행 (J2 ✅) → AI 워커가 RAG 저장·요약 ⏳ |
| 6-9 대화 목록 | `GET /api/v1/conversations` | ✅ · ⏳ | `origin` 추가. `summary`는 Kafka `conversation-summaries` 수신해 저장 (J2 ✅, V19). AI 워커가 보내기 전까지 `null` ⏳ |
| 6-10 대화 상세 | `GET /api/v1/conversations/{id}` | ✅ | 원문은 원문 공유 동의가 있을 때만 (`transcriptVisible`) |
| 6-11 대화 삭제 | `DELETE /api/v1/conversations/{id}` | ✅ | |
| 7 Prompt | — | ➖ | AI 서버 담당 |
| 8 대화 맥락 | gRPC `AudioRequest.history` | ✅ · ⏳ | BE 보관·전달 완료. AI는 LLM 입력에 반드시 반영 (J6) |
| 9 gRPC | `DialogueService/Converse`·`Speak` | ✅ · ⏳ | round_robin, 재시도, Circuit Breaker. `Speak`(J4)는 AI 구현 전까지 `UNIMPLEMENTED` → 텍스트만 보냄 ⏳ |
| 인지지표 수신 | Kafka `wilson.cognitive-metrics.v1` | ✅ · ⏳ | 수신 ✅. BE가 턴마다 `turn-analysis`(Protobuf) 발행 ✅ → AI 분석 워커가 계산해 발행 ⏳ (J1) |
| 11 일정 | `/api/v1/elderlies/{id}/schedules` | ✅ | 보호자·노인 모두 관리 (V14) |
| 12 분석 판정 | `GET /api/v1/elderlies/{id}/assessments` | ✅ | 기준값·5일 연속 판정, 매일 03:00 배치 (V16, `dementia_signal` 제거) |
| 13 대시보드 | `GET /api/v1/elderlies/{id}/dashboard` | ✅ | 오늘 요약·현재 판정·최근 대화 3건·지표 추이 |
| 14 알림 | `/api/v1/notifications` | ✅ | FCM HTTP v1 + 앱 내 목록, 야간 보류·07:00 묶음, 20:00 하루 요약 (V13) |
| 15 응급 | `/api/v1/elderlies/{id}/emergencies` | ✅ · ⏳ AI | BE 처리·목록·확인·WS `EMERGENCY`·gRPC `EmergencyFrame` 매핑 완료 (V17, J5). AI가 감지해 보내기 전까지는 발생하지 않음 ⏳ |
| 16 설정 | `/api/v1/elderlies/{id}/notification-settings`, `/api/v1/elderlies/{id}/settings` | ✅ | 16-1 보호자 알림 설정, 16-2 노인 설정 (V14) |
| 17 배포 | K8s | ✅ | `Dockerfile`, `k8s/base`(kustomize): ALB + ACM TLS 종료(I4), 내부 h2c, Secret, 헬스 프로브 |

## 0-2. 공통 규칙

- **권한**: 노인 정보·대화에 접근하려면 해당 노인과 **연결된 보호자**여야 한다. 아니면 `403 C005 NO_PERMISSION`.
  보호자 역할(`OWNER`/`MEMBER`)별 권한은 5-7 앞의 표를 본다.
- **대화 상태**: 시작 시 `WAITING` → 음성 턴 중 `PROCESSING` → `RESPONDING` → 턴 종료 시 `WAITING` → 종료 시 `FINISHED`.
  진행 중인 대화가 있는데 새 대화를 시작하면 이전 대화는 **자동 종료**된다.
- **잘못된 요청**: JSON 형식 오류, 쿼리 파라미터 누락·형식 오류·범위 초과 모두 `400 C002`.
- **시도 횟수 제한**: 기기 로그인은 같은 IP에서 10분에 10회 초과 시 `429 A004`.
- **시간**: 서버 내부 저장은 KST 기준 `LocalDateTime`(인지지표만 UTC `Instant`). 일정·방해 금지 시간은 **KST 고정**.
- **의료 표현 금지**: 보호자에게 보여주는 문구에 "치매·진단·위험" 같은 의료 판단 표현을 쓰지 않는다 (12-4).

## 0-3. 결정 완료 (2026-10-20)

v1.1에서 검토가 필요했던 항목을 아래처럼 결정했고 본문에 반영했다.

| 항목 | 결정 |
|---|---|
| 응답 형식 | 현행 유지: 성공 `{message, data}`, 오류 `{code, message, errors}` (1장) |
| 3-1, 3-3 경로 | 현행 유지: `POST /members`, `POST /auth/reissue` |
| Refresh Token 전달 | 요청 바디 (모바일 앱) |
| Validation 실패 상태 코드 | `400` |

---

# 1. API Design Convention

## Base URL

```
/api/v1
```

---

## Response Format

> v1.2 결정(0-3): 성공 여부는 HTTP 상태 코드로 판단하고, 본문에 `success` 필드를 두지 않는다.

### Success

```json
{
  "message": "SUCCESS",
  "data": {}
}
```

- `204 No Content` 응답은 본문이 없다.

---

### Error

```json
{
  "code": "M001",
  "message": "이미 사용 중인 이메일입니다.",
  "errors": [
    { "field": "email", "reason": "이메일 형식이 올바르지 않습니다." }
  ]
}
```

- `code`는 도메인 접두사 + 번호다 (`C` 공통, `A` 인증, `M` 회원, `E` 노인, `G` 보호자 연결, `CV` 대화, `AI` AI 서버 …). 앱은 `code`로 분기한다.
- `errors`는 입력값 검증 실패(`C002`)일 때만 채워지고, 나머지는 빈 배열이다.

---

## HTTP Status

| Status | Meaning       |
| ------ | ------------- |
| 200    | 조회·수정 성공      |
| 201    | 생성 성공         |
| 204    | 삭제·본문 없는 성공 |
| 400    | 잘못된 요청 (**Validation 실패 포함**, `C002`) |
| 401    | 인증 실패         |
| 403    | 권한 없음         |
| 404    | 리소스 없음        |
| 409    | 중복·상태 충돌      |
| 429    | 시도 횟수 초과      |
| 500    | 서버 오류         |
| 502·504 | AI 서버 오류·지연  |

422는 사용하지 않는다 (0-3).

---

## Authentication

JWT Access Token

```
Authorization: Bearer {AccessToken}
```

Refresh Token은 **로그인 응답 바디로 받아 앱 보안 저장소에 보관**하고, 재발급 요청 바디로 보낸다 (3-3). 쿠키는 쓰지 않는다 (모바일 앱).

---

## Pagination

```
GET /conversations?page=0&size=20
```

Response

```json
{
  "page":0,
  "size":20,
  "totalElements":35,
  "totalPages":2,
  "content":[]
}
```

---

# 2. Domain

Wilson Backend는 다음 Domain으로 구성된다.

```
Auth

User

Caregiver

Elderly

Conversation

Dashboard

Analysis

Notification

Schedule

Emergency

Settings
```

---

# 3. AUTH API

---

# 3-1 회원가입

> ✅ v1.2 결정: 경로는 `POST /api/v1/members` (회원 리소스 생성).
> 보호자 가입이 기본이고, 노인 본인도 가입할 수 있다(`memberType: ELDERLY`). 보호자가 등록한 노인은 가입하지 않고 3-5 기기 로그인을 쓴다.
> 📝 노인 본인 가입이면 동의(5-12)를 함께 받는다 — `consents`에 `SENSITIVE_INFO` 동의가 없으면 `400 CS001`.
> 현재 구현의 비밀번호 규칙은 8자 이상, 전화번호는 4-2에서 받는다.

```
POST /api/v1/members
```

## Description

회원가입을 수행한다.

---

## Authentication

필요 없음

---

## Request

```json
{
  "email":"test@test.com",
  "password":"password123!",
  "name":"홍길동",
  "memberType":"CAREGIVER"
}
```

노인 본인 가입 (📝 v1.2)

```json
{
  "email":"grandma@test.com",
  "password":"password123!",
  "name":"김할머니",
  "memberType":"ELDERLY",
  "consents":[
    {"type":"SENSITIVE_INFO","agreed":true,"version":1},
    {"type":"RESEARCH_RETENTION","agreed":false,"version":1},
    {"type":"TRANSCRIPT_SHARING","agreed":true,"version":1}
  ]
}
```

응답 `201` `{"id":1,"email":"...","name":"...","memberType":"CAREGIVER"}`

---

## Validation

Name

* 필수

* 최대 30자

Email

* 필수

* Email 형식

Password

* 8~20자

* 영문

* 숫자

* 특수문자 포함

Phone

* 숫자만 허용

---

## Business Rule

* Email은 중복될 수 없다.

* 탈퇴 회원 Email도 재사용 가능하다.

* 비밀번호는 BCrypt 암호화한다.

* 가입 즉시 AccessToken 발급하지 않는다.

* 로그인 후 Token 발급

---

## Success Response

201

```json
{
  "message":"SUCCESS",
  "data":{"id":1,"email":"test@test.com","name":"홍길동","memberType":"CAREGIVER"}
}
```

---

## Error

`409 M001` 이메일 중복 · `400 C002` 입력값 오류(이메일·비밀번호 형식 등)

---

## Service Flow

```
Controller

↓

AuthService

↓

Email Duplicate Check

↓

Password Encrypt

↓

User Save

↓

Response
```

---

## Test Case

✅ 정상 회원가입

✅ Email 중복

✅ Password 형식 오류

✅ Email 형식 오류

---

# 3-2 로그인

```
POST /api/v1/auth/login
```

---

## Request

```json
{
    "email":"test@test.com",
    "password":"password123!"
}
```

---

## Business Rule

로그인 성공 시

Access Token

Refresh Token

발급

Refresh Token은 Redis 저장

---

## Success Response

```json
{
    "message":"SUCCESS",
    "data":{
        "accessToken":"...",
        "refreshToken":"...",
        "tokenType":"Bearer",
        "memberId":1,
        "memberType":"CAREGIVER"
    }
}
```

---

## Error

`401 A001` 이메일 또는 비밀번호 불일치 (회원 없음과 구분하지 않는다 — 가입 여부 노출 방지).
계정 잠금은 v1.2 범위 밖이다.

---

## Service Flow

```
Controller

↓

AuthService

↓

Find User

↓

Password Match

↓

JWT Generate

↓

Redis Save Refresh Token

↓

Response
```

---

## Test Case

정상 로그인

비밀번호 틀림

회원 없음

잠긴 계정

---

# 3-3 Refresh Token

> ✅ v1.2 결정: 경로 `POST /api/v1/auth/reissue`, Refresh Token은 요청 바디로 받는다 (쿠키 사용 안 함).
> Refresh Token도 새로 발급(회전)되며, 이전 Refresh Token은 더 이상 쓸 수 없다.

```
POST /api/v1/auth/reissue
```

---

## Description

Refresh Token으로 Access Token 재발급

---

Authentication

필요 없음 (Refresh Token이 인증 수단)

---

Request

```json
{
    "refreshToken":"..."
}
```

---

Response

```json
{
    "message":"SUCCESS",
    "data":{
        "accessToken":"...",
        "refreshToken":"...",
        "tokenType":"Bearer"
    }
}
```

---

Error

INVALID_REFRESH_TOKEN

TOKEN_EXPIRED

---

Test

정상

만료

위조

Redis 없음

---

# 3-4 로그아웃

> ⚠️ v1.1 변경: 토큰 단건 블랙리스트 대신, 로그아웃 시점 이전에 발급된 **해당 회원의 모든 액세스 토큰**을 무효화한다
> (Redis `token-revoked:{memberId}`, TTL = 액세스 토큰 수명). Refresh Token이 회원당 1개라 로그인은 이미 단일 세션 기준이다.
> 이미 로그아웃한 토큰으로 다시 호출하면 `401`.
> 📝 v1.2: 로그아웃 시 그 회원의 푸시 토큰(4-5)도 삭제한다 (로그아웃한 기기로 알림이 가지 않도록).
>
> **(신규) 3-5 기기 로그인** `POST /api/v1/auth/device-login` — 인증 불필요
> 요청 `{"deviceCode":"012345"}` (6자리 숫자) → 응답은 3-2 로그인과 동일 (`memberType: ELDERLY`).
> 계정 없는 노인은 첫 로그인 때 이메일·비밀번호 없는 노인 회원이 생성된다.
> 오류: `401 A003` 잘못되거나 만료·사용된 코드, `429 A004` 같은 IP에서 10분에 10회 초과.

```
POST /api/v1/auth/logout
```

---

Description

Refresh Token 삭제

JWT BlackList 등록

---

Response

204

---

Test

정상

이미 로그아웃

---

# 4 USER API

> ⚠️ v1.1 변경
> - 4-1 응답에 `memberType`(`CAREGIVER`/`ELDERLY`) 추가. 노인 회원도 조회 가능.
> - 4-2·4-3·4-4는 **보호자만** (노인 회원은 `403 C005`). 노인 정보는 보호자가 5-4·5-5로 관리한다.
> - 4-2 전화번호: 하이픈 없는 숫자 10~11자리 (한국·일본 공통). 보낸 항목만 수정.
> - 4-3 응답 `204`. 오류 `400 M003` 현재 비밀번호 불일치, `400 M004` 동일 비밀번호.
> - 4-4 탈퇴: 이름·이메일·전화번호·비밀번호를 **즉시 파기**(같은 이메일로 재가입 가능), 노인과의 연결만 해제(노인·대화 이력은 유지), 모든 토큰 무효화.
>
> 📝 v1.2
> - 4-1: 노인 회원이면 응답에 `elderlyId`를 추가한다 (노인 기기가 11장 일정·16장 설정 API를 부를 때 사용).
> - 4-4: 탈퇴하려는 보호자가 어떤 노인의 **OWNER이고 그 노인에게 다른 보호자가 있으면** `409 G005` — 5-10으로 주보호자를 먼저 넘겨야 한다.
>   다른 보호자가 없으면 탈퇴할 수 있고, 그 노인은 보호자 없는 상태가 된다. **30일 안에** 노인 본인이 초대 코드(5-7)로 새 보호자를 연결하지 않으면 5-5 삭제 정책으로 파기한다.
> - 4-5 **푸시 토큰 등록** (신규) `PUT /api/v1/users/me/push-token` — 노인·보호자 공통, 앱 실행·토큰 갱신 시 호출.
>   요청 `{"token":"fcm-token","platform":"ANDROID"}` (`ANDROID`/`IOS`). 회원당 1개(마지막 기기)만 보관, 응답 `204`.

Wilson은 보호자(User)가 하나 이상의 Elderly를 관리한다.

---

# 4-1 내 정보 조회

```
GET /api/v1/users/me
```

Authentication

필수

---

Response

```json
{
    "id":1,
    "name":"홍길동",
    "email":"test@test.com",
    "phone":"01012345678",
    "createdAt":"..."
}
```

---

Test

정상

JWT 없음

---

# 4-2 회원정보 수정

```
PATCH /api/v1/users/me
```

Request

```json
{
    "name":"홍길동",
    "phone":"010..."
}
```

---

Business Rule

Email 수정 불가

---

Test

정상

이름 공백

전화번호 형식

---

# 4-3 비밀번호 변경

```
PATCH /api/v1/users/password
```

Request

```json
{
    "currentPassword":"...",
    "newPassword":"..."
}
```

---

Business Rule

현재 비밀번호 일치 확인

새 비밀번호는 기존과 달라야 함

---

Test

현재 비밀번호 오류

동일 비밀번호

정상 변경

---

# 4-4 회원 탈퇴

```
DELETE /api/v1/users/me
```

---

Business Rule

Soft Delete

Refresh Token 삭제

모든 기기 로그아웃

---

Test

정상

JWT 만료

---

# 5 ELDERLY API

Wilson 서비스의 핵심 도메인.

모든 Elderly는 하나 이상의 Caregiver와 연결될 수 있다.

> 📝 v1.2 보호자 역할: 노인마다 **OWNER(주보호자) 1명 + MEMBER 여러 명**. 노인을 등록한 보호자가 OWNER, 초대 코드(5-8)로 들어온 보호자는 MEMBER다.
> (기존 `care_relationship.is_primary`를 `role`로 바꾼다.)
>
> | 작업 | OWNER | MEMBER | 노인 본인 |
> |---|---|---|---|
> | 노인 조회(5-2·5-3), 정보 수정(5-4), 기기 연결 코드(5-6) | ✅ | ✅ | — |
> | 노인 삭제(5-5) | ✅ | ❌ | — |
> | 보호자 초대 코드 발급(5-7) | ✅ | ❌ | ✅ (OWNER가 없을 때만) |
> | 보호자 목록(5-9) | ✅ | ✅ | — |
> | 주보호자 넘기기(5-10) | ✅ | ❌ | — |
> | 연결 해제(5-11) | MEMBER 내보내기 | 본인만 나가기 | — |
> | 동의 조회(5-12) | ✅ | ✅ | ✅ |
> | 동의 변경(5-12) | ✅ (대리) | ❌ | ✅ (본인) |
> | 일정(11장) | ✅ | ✅ | ✅ |
> | 노인 설정(16장) | ✅ | ❌ | ✅ |

---

# 5-1 노인 등록

> 📝 v1.2: 등록한 보호자가 OWNER가 된다. 동의(5-12)를 함께 받는다 — `SENSITIVE_INFO` 동의가 없으면 `400 CS001`.
> 대리 동의이므로 `legalBasis`가 필수다 (`GUARDIANSHIP` 성년후견 / `FAMILY_DELEGATION` 가족 위임 / `OTHER`).

```
POST /api/v1/elderlies
```

---

Request

```json
{
    "name":"김철수",
    "birth":"1945-03-11",
    "gender":"MALE",
    "relationship":"FATHER",
    "consents":[
        {"type":"SENSITIVE_INFO","agreed":true,"version":1},
        {"type":"RESEARCH_RETENTION","agreed":false,"version":1},
        {"type":"TRANSCRIPT_SHARING","agreed":true,"version":1}
    ],
    "legalBasis":"FAMILY_DELEGATION"
}
```

---

Business Rule

회원당 최대 등록 가능 인원

(정책으로 관리)

---

Response

```json
{
    "elderlyId":15
}
```

---

Test

정상

이름 없음

생년월일 오류

---

# 5-2 노인 목록 조회

```
GET /api/v1/elderlies
```

---

Response

```json
[
  {
    "id":1,
    "name":"김철수",
    "age":81,
    "relationship":"FATHER"
  }
]
```

---

Test

정상

등록 없음

---

# 5-3 노인 상세 조회

```
GET /api/v1/elderlies/{elderlyId}
```

---

Response

```json
{
    "id":1,
    "name":"김철수",
    "birth":"1945-03-11",
    "gender":"MALE",
    "relationship":"FATHER",
    "connectedDevice":true
}
```

---

Error

ELDERLY_NOT_FOUND

NO_PERMISSION

---

# 5-4 노인 정보 수정

```
PATCH /api/v1/elderlies/{elderlyId}
```

---

수정 가능

* 이름

* 관계

* 생년월일

---

Test

정상

권한 없음

---

# 5-5 노인 삭제

> 📝 v1.2 (결정 B5): **OWNER만** 삭제할 수 있다 (MEMBER는 `403 C005`). 숨김 처리(Soft Delete)만 하던 것을 아래처럼 바꾼다.
> 노인 본인이 탈퇴하는 경로는 따로 두지 않는다 (민감정보 동의 철회 = 이 삭제와 같은 처리).
>
> | 데이터 | 처리 |
> |---|---|
> | 노인 실명·생년월일·성별·케어 메모 | **즉시 파기** (이름은 "삭제된 어르신"으로 바꾸고 나머지는 비움) |
> | 노인 회원 계정 | 4-4 탈퇴와 같이 익명화하고 모든 토큰 무효화 |
> | 대화 메시지 원문 | **즉시 삭제** (대화 행은 시각·유형만 남기고 숨김) |
> | Redis 대화 맥락 | 삭제 |
> | AI 쪽 RAG 개인 임베딩 | 삭제 요청 (⏳ AI 삭제 RPC 필요 — `ai-review-request.md`) |
> | 보호자 연결·일정·설정·동의 기록 | 파기 |
> | 인지지표(`cognitive_metric`) | **연구 보관 동의(`RESEARCH_RETENTION`)가 있으면** 가명으로 보관, 없으면 삭제 |
> | 노인 ↔ 가명 식별자 연결 | 끊는다 — `elderly.subject_id`를 새 무작위 값으로 바꿔, 보관된 지표가 더 이상 이 노인을 가리키지 않게 한다 |
>
> 응답 `204`.

```
DELETE /api/v1/elderlies/{elderlyId}
```

---

# 5-6 디바이스 연결

> ⚠️ v1.1 변경: 방향을 바꿨다. 보호자가 `POST /api/v1/elderlies/{elderlyId}/device-code`로 코드를 **발급**하면
> (응답 `{"deviceCode":"012345","expiresAt":"..."}`, 10분 유효·일회성), 노인 기기가 3-5 기기 로그인으로 연결한다.

```
POST /api/v1/elderlies/{elderlyId}/device
```

---

Request

```json
{
    "deviceCode":"ABCD-1234"
}
```

---

Business Rule

DeviceCode는 일회성

이미 연결된 Device는 재사용 불가

---

Test

정상 연결

잘못된 코드

만료 코드

이미 연결됨

---

# 5-7 보호자 초대 코드 발급 (📝 v1.2 신규)

> 결정 0: 보호자 연결에는 **노인별 초대 코드**가 필요하다. 노인 ID만으로는 연결할 수 없다.

```
POST /api/v1/elderlies/{elderlyId}/invite-code
```

권한: 그 노인의 **OWNER**, 또는 **노인 본인**(노인 회원 토큰, 그 노인에게 OWNER가 없을 때 — 본인 가입 노인·주보호자 탈퇴 후)

Response `201`

```json
{
    "message":"SUCCESS",
    "data":{"inviteCode":"K7Q2M9XD","expiresAt":"2026-10-21T14:00:00"}
}
```

Business Rule

- 영문 대문자·숫자 8자리, 헷갈리는 문자(`0`, `O`, `1`, `I`) 제외 (약 1조 가지)
- **24시간 유효, 1회용**. 노인당 유효한 코드는 1개 — 새로 발급하면 이전 코드는 무효
- Redis `invite-code:{code}` → elderlyId (TTL 24h), `invite-code:elderly:{elderlyId}` → code (이전 코드 무효화용)

Error: `403 C005` 권한 없음, `404 E001` 노인 없음, `503 E003` 코드 발급 실패(충돌 반복)

---

# 5-8 초대 코드로 보호자 연결 (📝 v1.2 변경)

> 기존 `POST /api/v1/care-relationships {"elderlyId":...}`는 **폐기**한다 (누구나 남의 노인과 연결할 수 있는 보안 문제).

```
POST /api/v1/care-relationships
```

권한: 보호자 회원

Request

```json
{
    "inviteCode":"K7Q2M9XD",
    "relation":"딸"
}
```

Response `201`

```json
{
    "message":"SUCCESS",
    "data":{"careRelationshipId":31,"elderlyId":15,"role":"MEMBER"}
}
```

Business Rule

- 코드가 가리키는 노인에 **MEMBER**로 연결한다. 그 노인에게 OWNER가 없으면 **OWNER**로 연결한다.
- 코드는 사용 즉시 폐기한다 (성공한 경우만).
- 같은 회원이 10분에 10회 넘게 시도하면 `429 G004` (코드 무작위 대입 방지).

Error: `400 G003` 잘못되었거나 만료된 코드, `409 G002` 이미 연결됨, `429 G004` 시도 횟수 초과, `404 G001` 보호자 회원 아님

---

# 5-9 노인의 보호자 목록 (📝 v1.2 신규)

```
GET /api/v1/elderlies/{elderlyId}/caregivers
```

권한: 연결된 보호자

```json
{
    "message":"SUCCESS",
    "data":[
        {"careRelationshipId":30,"name":"김보호","relation":"아들","role":"OWNER","me":true},
        {"careRelationshipId":31,"name":"김딸","relation":"딸","role":"MEMBER","me":false}
    ]
}
```

---

# 5-10 주보호자 넘기기 (📝 v1.2 신규)

```
PATCH /api/v1/elderlies/{elderlyId}/owner
```

권한: OWNER

Request `{"careRelationshipId":31}` — 같은 노인의 MEMBER여야 한다.

Business Rule: 대상은 OWNER가 되고, 기존 OWNER는 MEMBER가 된다. 응답 `204`.

Error: `403 C005` OWNER 아님, `400 G006` 대상이 이 노인의 MEMBER가 아님

---

# 5-11 연결 해제 (📝 v1.2 신규)

```
DELETE /api/v1/care-relationships/{careRelationshipId}
```

- MEMBER 본인은 언제든 나갈 수 있다.
- OWNER는 다른 MEMBER를 내보낼 수 있다.
- **OWNER 자신은 해제할 수 없다** → `409 G005` (먼저 5-10으로 넘기거나, 노인을 삭제 5-5)
- 응답 `204`

---

# 5-12 동의 (📝 v1.2 신규, 결정 B4)

> 법률 자문 전까지는 **동의·철회 기록만** 남긴다. 기록은 고치지 않고 이력으로 쌓는다 (언제·누가·어떤 근거로·몇 번째 약관 버전에 동의했는지).

| 동의 종류 | 필수 | 의미 |
|---|---|---|
| `SENSITIVE_INFO` | ✅ | 음성·대화·건강 관련 민감정보 처리. 없으면 서비스 이용 불가. 철회하려면 5-5 삭제 |
| `RESEARCH_RETENTION` | | 삭제·철회 후에도 인지지표를 가명으로 연구 보관 |
| `TRANSCRIPT_SHARING` | | 보호자에게 **대화 원문** 공개 (없으면 요약·지표만 — 6-10) |

조회

```
GET /api/v1/elderlies/{elderlyId}/consents
```

```json
{
    "message":"SUCCESS",
    "data":[
        {"type":"SENSITIVE_INFO","agreed":true,"version":1,"grantedBy":"OWNER","legalBasis":"FAMILY_DELEGATION","updatedAt":"..."},
        {"type":"TRANSCRIPT_SHARING","agreed":false,"version":1,"grantedBy":"SELF","legalBasis":"SELF","updatedAt":"..."}
    ]
}
```

변경

```
PUT /api/v1/elderlies/{elderlyId}/consents/{type}
```

```json
{"agreed":true,"version":1,"legalBasis":"FAMILY_DELEGATION"}
```

- 노인 본인(`legalBasis`는 `SELF`로 저장) 또는 OWNER(대리, `legalBasis` 필수)만 변경할 수 있다. 응답 `204`.
- 노인 본인과 OWNER의 기록이 다르면 **최신 기록**이 효력을 가진다.
- `SENSITIVE_INFO`를 `false`로 바꿀 수는 없다 → `400 CS002` (철회는 5-5 삭제).

---

# 6. Conversation Domain

Conversation Domain은 Wilson 서비스의 핵심이다.

Conversation은 단순 채팅이 아니라

* 음성
* 대화 Context
* Prompt
* AI Server
* Memory
* 감정 분석

을 모두 포함한다.

---

# Conversation State

```
WAITING

LISTENING

PROCESSING

RESPONDING

FINISHED

FAILED
```

---

# 6-1 대화 시작

> ⚠️ v1.1 변경: **노인 기기 토큰**으로만 호출한다. 노인은 토큰으로 결정되므로 요청은 `{"conversationType":"DAILY"}`만 받는다.
> 진행 중인 대화가 있으면 409 대신 **이전 대화를 자동 종료**한다 (앱 비정상 종료 대비). 시작 시점에는 Redis에 아무것도 만들지 않고, 대화 맥락은 첫 턴부터 쌓인다 (8장 참고).

```
POST /api/v1/conversations
```

---

## Description

새로운 대화를 생성한다.

Redis에 Session Context를 생성한다.

---

## Authentication

필수

---

## Request

```json
{
    "elderlyId":1,
    "conversationType":"DAILY"
}
```

---

conversationType

```
DAILY

MEDICATION

MEAL

WALK

MEMORY

FREE
```

---

## Business Rule

* 동시에 하나의 Conversation만 가능
* Redis Session 생성
* Conversation State = WAITING

---

## Success

201

```json
{
    "conversationId":15,
    "state":"WAITING"
}
```

---

## Service Flow

```
Controller

↓

ConversationService

↓

Conversation 생성

↓

Redis Session 생성

↓

Response
```

---

## Test

✅ 정상 생성

✅ Elderly 없음

✅ 이미 진행 중인 Conversation 존재

---

# 6-2 대화 시작(선제적 알림)

> 📝 v1.2 재설계 (결정 C4~C6). 아래 v1.0 본문(스케줄러가 직접 호출)은 폐기한다.
> 노인 기기는 꺼져 있거나 앱이 백그라운드일 수 있으므로, **BE가 FCM 푸시로 기기를 깨우고 앱이 대화를 시작**한다.
>
> **흐름**
> 1. 스케줄러가 매분 실행 시각이 된 일정(11장)을 찾는다. 노인 설정(16장)에서 선제 대화가 꺼져 있거나 **방해 금지 시간**(기본 22:00~07:00)이면 건너뛴다.
> 2. 선제 대화 기록(`proactive_trigger`, 상태 `SENT`)을 만들고, 노인 기기(4-5 푸시 토큰)로 FCM 푸시를 보낸다.
>    J4: **첫 안부 문장(`greeting`)을 BE가 만들어** 알림으로 표시하고(제목 `윌슨`, 본문 = 문장), 데이터에도 담는다.
>    `{"type":"PROACTIVE","triggerId":"8f1c…","conversationType":"MEAL","greeting":"할머니, 오늘 점심은 뭐 드셨어요?"}`
>    문장 규칙 (호칭: 성별 여 → 할머니, 남 → 할아버지, 모름 → 어르신)
>    - `MEAL`: `{호칭}, 오늘 {아침·점심·저녁}은 뭐 드셨어요?` (10:30 전 아침, 15:00 전 점심, 그 뒤 저녁)
>    - `MEDICATION`: `{호칭}, {라벨 또는 "약"} 드실 시간이에요. 챙겨 드셨어요?`
>    - `WALK`: `{호칭}, 오늘 산책은 다녀오셨어요?`
>    - `DAILY`: `{호칭}, 오늘은 기분이 어떠세요?`
> 3. 앱이 푸시를 받으면 노인 토큰으로 `POST /api/v1/conversations/proactive` `{"triggerId":"8f1c…"}`를 호출한다.
>    BE는 일정 유형으로 대화를 만들고(진행 중인 대화는 6-1처럼 자동 종료) `201 {"conversationId":17,"state":"WAITING"}`를 응답한다.
> 4. 앱이 음성 WebSocket에 연결해 `{"type":"PROACTIVE_START"}`를 보내면, BE가 2번의 문장을 AI 발화로 대화 이력에 남기고 AI `Speak`(TTS)로 읽게 해 음성 조각 → `COMPLETED{text: 문장}`을 보낸다(`TRANSCRIPT` 없음). AI가 실패·미지원이면 음성 없이 `COMPLETED`만 보내고 앱이 기기 음성으로 읽는다.
>    이후는 일반 음성 턴과 같다 (`docs/websocket-voice-protocol.md`).
> 5. **무응답 (C5):** 푸시 후 10분 안에 그 대화에서 노인 발화가 없으면 푸시를 1회 다시 보낸다(`RETRIED`).
>    다시 10분 동안 발화가 없으면 `MISSED`로 바꾸고 보호자에게 `PROACTIVE_NO_RESPONSE` 알림(14장)을 보낸다. 노인이 발화하면 `RESPONDED`.
>
> - `triggerId`는 30분 유효, 대화는 한 번만 만든다. 이미 대화를 만든 트리거로 다시 부르면(재푸시) **그 대화를 그대로 돌려준다**(`201`, 대화가 끝났으면 `409 SC004`).
>   오류: `404 SC003` 없는 트리거, `409 SC004` 만료·무응답 처리됨, `403 C005` 다른 노인의 트리거.
> - 여러 BE Pod가 같은 일정을 중복 발송하지 않도록 스케줄러는 Redis 락(분 단위 키 `SET NX`)을 잡은 Pod만 실행한다.
> - J4로 `ERROR AI003` 응답은 폐지했다 (`Speak` 계약: `docs/ai-integration-contract.md` 1-2장). 재푸시(5번)도 같은 문장을 쓴다.
> - 이미 말이 오간 선제 대화에서 `PROACTIVE_START`를 다시 보내면 `ERROR CV005`.

```
POST /api/v1/conversations/proactive
```

---

## Description

Scheduler가 호출하는 API

AI가 먼저 말을 거는 대화를 시작한다.

---

## Request

```json
{
    "elderlyId":1,
    "triggerType":"MEAL"
}
```

---

triggerType

```
MEAL

MEDICATION

WALK

SLEEP

CUSTOM
```

---

## Business Rule

* Scheduler만 호출 가능
* 사용자 직접 호출 불가
* Notification 생성

---

## Response

```json
{
    "conversationId":17
}
```

---

## Test

정상

권한 없음

중복 호출

---

# 6-3 음성 업로드

> ⚠️ v1.1 변경: **6-3 ~ 6-7은 폐기**하고 `WS /ws/v1/conversations/{conversationId}/voice` 하나로 대체한다.
> AI 서버는 STT·답변 생성·TTS를 gRPC `Converse` 한 번의 서버 스트리밍으로 처리하며, inbound REST(콜백)를 받지 않는다.
> 프레임 규약은 `docs/websocket-voice-protocol.md`, AI 계약은 `src/main/proto/wilson/dialogue/v1/dialogue.proto`.
> 노인 발화(STT 원문)와 AI 답변은 같은 `turn_id`로 `message` 테이블에 저장된다.

```
POST /api/v1/conversations/{conversationId}/voice
```

---

## Description

사용자의 음성을 업로드한다.

---

## Request

multipart/form-data

```
voice.wav
```

---

Validation

```
wav

mp3

m4a

최대 30MB
```

---

Business Rule

업로드 후

STT Queue 등록

Conversation State

↓

PROCESSING

---

Response

```json
{
    "uploadId":33,
    "status":"PROCESSING"
}
```

---

Service Flow

```
Upload

↓

S3

↓

Queue

↓

STT

↓

Redis
```

---

Test

정상

확장자 오류

용량 초과

Conversation 없음

---

# 6-4 STT 완료 Callback

```
POST /internal/stt/callback
```

---

Description

STT 서버 내부 Callback

외부 접근 금지

---

Request

```json
{
    "conversationId":15,
    "text":"오늘 날씨가 좋네."
}
```

---

Business Rule

Conversation 존재 확인

Redis Context 저장

Prompt 생성

AI Queue 등록

---

Response

204

---

# 6-5 AI 응답 생성

```
POST /internal/ai/generate
```

---

Description

Prompt Builder가 호출

gRPC Client 사용

---

Request

```json
{
    "conversationId":15
}
```

---

Service Flow

```
Conversation 조회

↓

Redis Context

↓

Long Memory 조회

↓

Prompt Builder

↓

gRPC

↓

AI Server
```

---

Business Rule

Prompt는

Service에서 직접 생성하지 않는다.

PromptBuilder Component 사용

---

Test

Redis 없음

Memory 없음

Prompt 생성 실패

---

# 6-6 AI 응답 Callback

```
POST /internal/ai/callback
```

---

Request

```json
{
    "conversationId":15,
    "response":"안녕하세요. 오늘 식사는 하셨나요?"
}
```

---

Business Rule

Response 저장

Redis Context Update

TTS Queue 등록

---

State

```
RESPONDING
```

---

# 6-7 TTS 완료 Callback

```
POST /internal/tts/callback
```

---

Request

```json
{
    "conversationId":15,
    "audioUrl":"..."
}
```

---

Business Rule

Conversation 완료

State

↓

FINISHED

---

Response

204

---

# 6-8 대화 종료

```
PATCH /api/v1/conversations/{conversationId}/finish
```

---

Description

사용자가 직접 종료

---

Business Rule

Redis Session 제거

Conversation 종료

Analysis Queue 등록

---

State

```
FINISHED
```

---

Test

정상

이미 종료

없는 Conversation

---

# 6-9 대화 목록

> ⚠️ v1.1 변경: 연결된 보호자만 조회. `elderlyId` 필수, `date`(yyyy-MM-dd) 선택, `size` 최대 100, 최신순.
> 항목은 `{conversationId, conversationType, state, startedAt, endedAt}` — `summary`는 생성 주체가 정해지면 추가 (`docs/ai-review-request.md` 4번).
> 6-10 상세의 메시지 `role`은 `ELDERLY` / `AI`, 각 메시지에 `createdAt` 포함. 6-11 삭제는 연결된 보호자만, `204`.
>
> 📝 v1.2 (결정 B1): 보호자는 기본적으로 **요약·지표만** 본다.
> - 6-9 각 항목에 `summary`(AI 생성 전에는 `null`)와 `origin`(`USER` 직접 시작 / `PROACTIVE` 선제 대화)을 추가한다.
> - 6-10은 노인의 `TRANSCRIPT_SHARING` 동의(5-12)가 **유효할 때만** `messages`를 채운다. 동의가 없으면 `transcriptVisible: false`, `messages: []`.
>   ```json
>   {"conversationId":15,"conversationType":"MEAL","origin":"PROACTIVE","startedAt":"...","endedAt":"...",
>    "summary":null,"transcriptVisible":false,"messages":[]}
>   ```
> - J2: `summary`는 대화 종료 후 AI 워커가 만들어 Kafka로 보내 준다 (`docs/ai-integration-contract.md` 4장). 도착 전·실패 시에는 `null`이고, 그동안 동의가 없는 보호자는 대화 유형·시각·지표만 볼 수 있다.

```
GET /api/v1/conversations
```

---

Query

```
page

size

elderlyId

date
```

---

Response

```json
{
  "content":[
      {
          "conversationId":15,
          "startedAt":"...",
          "endedAt":"...",
          "summary":"오늘 산책 이야기를 나눔"
      }
  ]
}
```

---

# 6-10 대화 상세

```
GET /api/v1/conversations/{conversationId}
```

---

Response

```json
{
    "conversationId":15,

    "messages":[
        {
            "role":"USER",
            "text":"안녕"
        },
        {
            "role":"AI",
            "text":"안녕하세요."
        }
    ]
}
```

---

Business Rule

Message는 시간순 정렬

---

# 6-11 대화 삭제

```
DELETE /api/v1/conversations/{conversationId}
```

Soft Delete

---

# 7. Prompt Domain

> ➖ v1.1: AI 명세상 BE는 AI 서버에 프롬프트를 보내지 않는다 (`AudioRequest`에 오디오·식별자만 있음).
> 프롬프트 조합은 AI 서버가 담당한다. 개인화 정보 전달 방식은 AI 팀과 협의 중 (`docs/ai-review-request.md` 2번).

여기부터는 Wilson의 핵심이다.

Prompt는 절대로 Controller나 Service에서 문자열을 이어붙여 생성하지 않는다.

---

## Prompt Builder

```
Conversation

↓

Context

↓

Memory

↓

Today's Schedule

↓

Emotion Score

↓

System Prompt

↓

PromptBuilder

↓

gRPC
```

---

PromptBuilder Interface

```java
public interface PromptBuilder {

    Prompt build(
            Elderly elderly,
            Conversation conversation,
            Context context,
            Memory memory
    );

}
```

---

Builder Rule

Prompt는

* 역할
* 현재 상황
* 대화 이력
* 장기 기억
* 오늘 일정
* 감정 상태

를 조합하여 생성한다.

---

# 8. Context Management

> ✅ v1.2: **BE가 대화 맥락을 보관해 매 턴 AI에 보낸다** (AI 서버는 무상태). 아래 v1.0 본문(키·값·24시간 TTL)은 다음으로 대체한다.
> - Redis 리스트 `conversation-context:{conversationId}`에 최근 **10턴**(노인 발화 + AI 답변)을 오래된 순으로 보관한다.
> - 턴 시작·기록 때마다 TTL **30분을 다시 건다** (Sliding Expiration — 어르신의 긴 침묵 중 만료 방지).
> - 만료됐거나 Redis 장애면 DB `message`에서 다시 만든다. 맥락은 사라지지 않고, DB까지 실패하면 맥락 없이 대화를 이어간다.
> - 대화 종료(6-8)·자동 종료 시 삭제한다.
> - AI에는 gRPC `AudioRequest.history`로 보낸다. ⏳ AI가 LLM에 반영해야 효과가 난다 (`ai-review-request.md` 0-1).
> - 설정: `wilson.conversation.context.ttl`, `max-turns`

Conversation Context는 Redis에서 관리한다.

---

Redis Key

```
conversation:{conversationId}
```

---

Value

```json
{
    "summary":"...",
    "messages":[]
}
```

---

TTL

```
24 Hour
```

---

Business Rule

Conversation 종료

↓

PostgreSQL 저장

↓

Redis 삭제

---

# 9. gRPC API

> ⚠️ v1.1 변경: 아래 `GenerateConversation`(텍스트 입출력) 대신 AI 팀 명세 `DialogueService/Converse`를 따른다.
> 오디오 bytes 단일 입력 → `MetadataFrame`(STT) → `AudioChunk`(TTS) → `CompletionFrame` / `ErrorFrame` 서버 스트리밍.
> 첫 프레임 **20초** / 전체 **60초** 타임아웃 (AI STT 타임아웃 15초보다 길게), **첫 프레임 수신 전 실패만** 최대 3회 지수 백오프 재시도(중복 재생 방지), Circuit Breaker 적용.
> 대상은 `dns:///<headless-service>:50051`, 클라이언트 `round_robin` 분산 (17장).
> 설정: `wilson.ai.grpc.*` (`AI_GRPC_TARGET`).

Wilson Backend

↓

AI Server

---

RPC

```
GenerateConversation
```

---

Request

```protobuf
message ConversationRequest {

    string prompt = 1;

    string elderlyName = 2;

    string emotion = 3;

}
```

---

Response

```protobuf
message ConversationResponse {

    string text = 1;

}
```

---

Timeout

```
10 Second
```

---

Retry

```
3회
```

Exponential Backoff

---

Circuit Breaker

필수 적용

---

# 10. TDD Test Strategy

Conversation Domain은 Wilson의 핵심 도메인이므로 단순 성공 케이스만 검증하지 않는다.

## Unit Test

* Conversation 생성
* 중복 Conversation 방지
* PromptBuilder 정상 동작
* Context 생성 및 삭제
* State 변경 검증
* Redis 저장/조회(Mock)
* gRPC Client(Mock)
* Scheduler Trigger(Mock)

## Integration Test

* 대화 시작 → 종료 전체 플로우
* Redis Context 생성 및 제거
* PostgreSQL 영속화
* JWT 인증
* Controller Validation

## E2E Test (향후)

```
RN App

↓

Conversation Start

↓

Voice Upload

↓

STT

↓

AI

↓

TTS

↓

Conversation Finish

↓

Dashboard Update
```

---

> 아래 11~17장은 **v1.2 (2026-10-20 결정)** 이다. 구현 상태는 0장 표, 결정 배경은 `docs/decisions.md`.

# 11. Schedule (일정)

선제적 대화(6-2)를 언제 걸지 정하는 노인별 반복 일정. **보호자(OWNER·MEMBER)와 노인 본인 모두** 관리할 수 있다 (결정 C2).

| 항목 | 규칙 |
|---|---|
| `type` | `MEDICATION` 복약 · `MEAL` 식사 · `WALK` 산책 · `DAILY` 안부 (대화 유형과 같은 값) |
| `label` | 선택, 30자 이하 (예: "혈압약"). 푸시·대시보드 표시용 |
| `time` | `HH:mm`, **KST 고정** |
| `daysOfWeek` | `MON`~`SUN` 1~7개, 중복 불가 |
| `enabled` | 기본 `true` |

## 11-1 일정 등록

```
POST /api/v1/elderlies/{elderlyId}/schedules
```

```json
{"type":"MEDICATION","label":"혈압약","time":"08:30","daysOfWeek":["MON","TUE","WED","THU","FRI","SAT","SUN"],"enabled":true}
```

응답 `201 {"scheduleId":3}`

- 같은 노인에게 **같은 시각·겹치는 요일**의 일정이 있으면 `409 SC002` (같은 시각에 선제 대화 두 개 방지)

## 11-2 일정 목록

```
GET /api/v1/elderlies/{elderlyId}/schedules
```

시각순. 항목 `{scheduleId, type, label, time, daysOfWeek, enabled, createdBy: "CAREGIVER"|"ELDERLY", updatedAt}`

## 11-3 일정 수정 · 11-4 삭제

```
PATCH  /api/v1/schedules/{scheduleId}     (보낸 항목만 수정, 200)
DELETE /api/v1/schedules/{scheduleId}     (204)
```

Error: `404 SC001` 일정 없음, `409 SC002` 시각 중복, `403 C005` 권한 없음, `400 C002` 입력값 오류

---

# 12. Analysis (분석 판정)

AI 서버가 보낸 인지지표(`cognitive_metric`, Kafka — `docs/kafka-cognitive-metrics.md`)로 **BE가** 기준값을 만들고 변화를 판정한다 (AI 의사결정 로그: cut-off·flag는 BE 담당).
기존 `dementia_signal` 테이블(기억·어휘·감정 점수)은 이 설계로 **대체**한다.

## 12-1 판정 지표 (결정 D1)

11개 지표는 모두 저장하고, 판정은 아래 4개로 시작한다.

| 지표 | 보호자 표시 이름 | 나빠지는 방향 |
|---|---|---|
| `subsequent_onset_latency_ms` | 대답까지 걸리는 시간 | ↑ |
| `filler_ratio` | "음…" 같은 머뭇거림 | ↑ |
| `content_info_unit_ratio` | 대화에 담긴 내용 | ↓ |
| `words_per_utterance` | 한 번에 말하는 길이 | ↓ |

## 12-2 기준값 (결정 D2)

- 노인·지표별로, 분석 가능한(`analyzable=true`) 지표가 처음 들어온 날부터 **14일 동안의 일별 평균**으로 평균·표준편차를 만든다.
- 14일 중 데이터가 있는 날이 7일 미만이면 7일이 될 때까지 기간을 늘린다. 기준값이 생기기 전에는 판정하지 않는다 (상태 `COLLECTING`).
- 기준값은 한 번 만들면 **고정**한다 (천천히 나빠지는 변화를 기준값이 따라가 버리지 않도록).
- 표준편차가 너무 작으면(평균의 5% 미만) 평균의 5%를 표준편차로 쓴다 (작은 흔들림을 이상으로 보지 않도록).
- 그래도 표준편차가 0이면(예: 머뭇거림이 한 번도 없던 분, 평균 0) 그 지표는 판정하지 않는다.
- 기준값을 만든 기간의 날은 판정하지 않는다 (기준값 자신과 비교하게 되므로).

## 12-3 판정 (결정 D3·D4)

- 매일 **03:00 KST** 배치가 전날 지표의 일별 평균을 계산한다.
- `z = (일평균 − 기준평균) / 기준표준편차`를 나빠지는 방향 기준으로 보고 **z ≥ 2**이면 그날 그 지표는 "벗어남"이다.
- **데이터가 있는 날 기준 5일 연속** 벗어나면 그 지표를 판정한다. 벗어나지 않은 날이 오면 연속 횟수는 0으로 돌아간다. 대화가 없는 날은 건너뛴다 (연속 횟수 유지).
- 위험도: 판정된 지표 0개 `LOW` · 1개 `MEDIUM` · 2개 이상 `HIGH`
- 위험도가 올라간 날(LOW→MEDIUM, →HIGH) OWNER에게 `PATTERN_CHANGE` 알림 (14장)
- 저장: `elder_baseline`(노인·지표별 평균·표준편차·기간), `daily_assessment`(노인·날짜별 위험도·지표별 z·연속 횟수)
- 지표는 가명 식별자로 쌓이므로 `elderly.subject_id`로 이어서 계산한다.
- 대화가 전혀 없던 날은 판정을 남기지 않는다. 위험도는 직전 판정을 그대로 이어 간다.
- 노인을 삭제하면(5-5) 기준값·판정 이력도 지운다.

## 12-4 보호자 표시 문구 (결정 D5)

"치매·진단·위험·질환·이상" 같은 의료 표현은 쓰지 않는다. `LOW`/`MEDIUM`/`HIGH` 값은 화면에 그대로 보이지 않는다.

| 상태 | 문구 (초안) |
|---|---|
| `COLLECTING` | 평소 대화 모습을 알아가는 중이에요 (약 2주) |
| `LOW` | 평소와 비슷한 대화를 나누고 계세요 |
| `MEDIUM` | 최근 며칠 평소와 조금 다른 대화 모습이 보여요 |
| `HIGH` | 최근 며칠 평소와 다른 대화 모습이 이어지고 있어요. 어르신과 이야기 나눠 보시고, 걱정되시면 전문가와 상담해 보세요 |

## 12-5 판정 이력 조회

```
GET /api/v1/elderlies/{elderlyId}/assessments?from=2026-10-01&to=2026-10-20
```

권한: 연결된 보호자 (노인 본인은 `403 C005`). `from`·`to` 필수, 시작일·종료일 포함 최대 90일 (넘거나 순서가 바뀌면 `400 C002`). 날짜순.

```json
{"message":"SUCCESS","data":[
  {"date":"2026-10-19","level":"MEDIUM","message":"최근 며칠 평소와 조금 다른 대화 모습이 보여요",
   "flaggedMetrics":[{"metric":"subsequent_onset_latency_ms","label":"대답까지 걸리는 시간"}]}
]}
```

---

# 13. Dashboard (보호자 대시보드)

```
GET /api/v1/elderlies/{elderlyId}/dashboard?period=WEEK
```

권한: 연결된 보호자 (노인 본인은 `403 C005`). `period`는 `WEEK`(오늘 포함 최근 7일, 기본) / `MONTH`(오늘 포함 최근 30일). 그 밖의 값은 `400 C002`.

```json
{"message":"SUCCESS","data":{
  "today":{"conversationCount":3,"totalMinutes":12,"proactiveResponded":2,"proactiveMissed":0},
  "assessment":{"level":"LOW","message":"평소와 비슷한 대화를 나누고 계세요","since":"2026-10-18"},
  "recentConversations":[
    {"conversationId":15,"conversationType":"MEAL","origin":"PROACTIVE","state":"FINISHED","startedAt":"...","endedAt":"...","summary":null}
  ],
  "trends":[
    {"metric":"subsequent_onset_latency_ms","label":"대답까지 걸리는 시간","unit":"ms",
     "baseline":{"mean":820.0,"std":110.0},
     "points":[{"date":"2026-10-14","value":790.0},{"date":"2026-10-15","value":845.5}]}
  ]
}}
```

- `today`: 오늘(KST) 시작한 대화 기준. `totalMinutes`는 종료된 대화의 길이 합. `proactiveResponded`/`proactiveMissed`는 오늘 예정됐던 선제 대화(6-2) 중 응답/무응답 수
- `assessment`: 가장 최근 판정(12장). `since`는 지금 위험도로 처음 판정된 날. 판정이 아직 없으면 `{"level":"COLLECTING","message":"평소 대화 모습을 알아가는 중이에요 (약 2주)","since":null}`
- `recentConversations`: 최근 3건, 항목은 대화 목록(6-9)과 같은 형식. `summary`는 AI 요약 생성 전까지 `null`
- `trends`: 12-1 판정 지표 4개의 일별 평균(항상 4개, 12-1 순서). 데이터 없는 날은 생략. 그 지표의 기준값이 아직 없으면 `baseline`은 `null`. `unit`: `ms`(대답 시간) · `ratio`(머뭇거림·내용 비율) · `words`(한 번에 말하는 길이)

---

# 14. Notification (알림)

## 14-1 알림 종류 (결정 E2·E3)

| type | 예시 문구 | 받는 사람 | 22:00~07:00 |
|---|---|---|---|
| `PATTERN_CHANGE` | 어머님의 대화 모습이 최근 평소와 조금 달라졌어요 | OWNER + 설정에서 켠 MEMBER | 07:00에 발송 |
| `PROACTIVE_NO_RESPONSE` | 08:30 복약 안부에 응답이 없으셨어요 | OWNER + 켠 MEMBER | 07:00에 발송 |
| `EMERGENCY` | 어머님 대화에서 넘어지셨다는 말씀이 있었어요. 바로 연락해 보세요 | **연결된 보호자 전원** | **즉시** (끌 수 없음) |
| `DAILY_SUMMARY` | 오늘 대화 3번, 12분 나누셨어요 (매일 20:00) | OWNER + 켠 MEMBER | 해당 없음 |

기존 `NotificationType`(`PROACTIVE_CARE`, `DEMENTIA_ALERT`)은 위 4개로 대체한다. 알림은 보호자에게만 간다 (노인 기기 푸시는 6-2 선제 대화용).

## 14-2 발송 규칙 (결정 E1·E4)

- 알림을 먼저 DB에 저장(앱 내 목록)하고 그다음 **FCM 푸시**를 보낸다. 푸시가 실패해도 목록에는 남는다.
- 22:00~07:00(KST)에는 `EMERGENCY` 외 푸시를 보류하고 **07:00에 노인별로 묶어** 보낸다 ("새 알림 3건").
- FCM이 "등록되지 않은 토큰"이라고 응답하면 그 푸시 토큰을 지운다. 일시 오류는 3회까지 재시도한다.
- 대화 원문을 알림 문구에 넣지 않는다.

## 14-3 목록 · 14-4 읽음 · 14-5 안 읽은 수

```
GET   /api/v1/notifications?page=0&size=20        최신순, 항목 {notificationId, type, elderlyId, title, body, read, createdAt}
PATCH /api/v1/notifications/{notificationId}/read  204
PATCH /api/v1/notifications/read-all               204
GET   /api/v1/notifications/unread-count           {"count":3}
```

Error: `404 N001` 알림 없음(본인 알림이 아니어도 404)

---

# 15. Emergency (응급)

## 15-1 감지 (결정 G1) — ⏳ AI

AI가 발화에서 응급 신호를 감지하면 gRPC 응답 스트림에 응급 프레임을 보낸다 (제안: `EmergencyFrame{category}`, `ai-review-request.md`).
감지 기준·키워드는 AI가 담당한다. `category`: `FALL` 낙상 · `PAIN` 통증 · `BREATHING` 호흡 · `HELP_REQUEST` 도움 요청 · `OTHER`

## 15-2 BE 처리 (결정 G2)

1. 응급 기록 저장 (`emergency`: 노인, 대화, `turn_id`, category, 감지 시각)
2. 연결된 보호자 **전원**에게 `EMERGENCY` 알림을 **즉시** 푸시 (방해 금지 시간 무시)
3. 노인 앱에 WebSocket `{"type":"EMERGENCY","category":"FALL"}` → 앱이 **내장 안내 음성**을 먼저 재생
   ("보호자분께 바로 알렸어요. 무리해서 움직이지 마시고 잠시만 기다려 주세요.") 후 AI 답변을 이어서 재생
4. **119 자동 연결은 하지 않는다** (오탐 위험)
5. 같은 노인에게 10분 안에 같은 category가 다시 감지되면 기록만 하고 알림은 보내지 않는다
6. 기록·알림이 실패해도 노인 앱에는 `EMERGENCY`를 보내고 대화 턴은 이어 간다 (안내 음성이 먼저)

알림 문구 (대화 원문은 넣지 않는다): 제목 `{이름}님 확인이 필요해요`, 본문 `{이름}님 대화에서 {넘어지셨다는|아프시다는|숨쉬기 힘드시다는|도움을 청하시는|걱정되는} 말씀이 있었어요. 바로 연락해 보세요`

## 15-3 목록 · 15-4 확인

```
GET   /api/v1/elderlies/{elderlyId}/emergencies?page=0&size=20   {emergencyId, category, detectedAt, acknowledgedAt, acknowledgedBy}
PATCH /api/v1/emergencies/{emergencyId}/acknowledge              204 — 보호자 한 명이 확인하면 모두에게 확인됨으로 보인다
```

- `acknowledgedBy`: 처음 확인한 보호자 이름 (확인 전 `null`). 이미 확인된 기록을 다시 확인해도 204이며 처음 확인한 사람이 유지된다.

권한: 연결된 보호자 (노인 본인·연결되지 않은 보호자는 `403 C005`). Error: `404 EM001` 응급 기록 없음

---

# 16. Settings (설정)

## 16-1 보호자 알림 설정 (노인별)

```
GET   /api/v1/elderlies/{elderlyId}/notification-settings
PATCH /api/v1/elderlies/{elderlyId}/notification-settings
```

```json
{"patternChange":true,"proactiveNoResponse":true,"dailySummary":true}
```

- 요청한 보호자 **본인의** 설정이다. 기본값: OWNER는 모두 켬, MEMBER는 모두 끔
- `EMERGENCY`는 설정 항목이 없다 (항상 받음)

## 16-2 노인 설정

```
GET   /api/v1/elderlies/{elderlyId}/settings
PATCH /api/v1/elderlies/{elderlyId}/settings
```

```json
{"proactiveEnabled":true,"quietHoursStart":"22:00","quietHoursEnd":"07:00"}
```

- 권한: OWNER, 노인 본인 (조회는 연결된 보호자 전체)
- `HH:mm`, 시작과 끝이 같으면 `400 C002`. 자정을 넘기는 구간 허용 (22:00~07:00)

---

# 17. Deployment (배포, 결정 I)

| 항목 | 결정 |
|---|---|
| 플랫폼 | **K8s** (AI 서버와 같은 클러스터). 매니페스트는 BE 레포 `k8s/`(kustomize, AI 레포와 같은 구조) |
| 외부 TLS | App → **AWS ALB(Ingress, AWS Load Balancer Controller)에서 ACM 인증서로 TLS 종료** (HTTPS·WSS, 결정 I4). ALB → BE는 HTTP/1.1 (WebSocket 업그레이드 허용, 유휴 타임아웃은 음성 대화 길이 이상 — 3600초) |
| 내부 BE → AI | **h2c** (평문 HTTP/2), `AI_GRPC_TARGET=dns:///orchestrator-server-headless.<namespace>.svc.cluster.local:50051`, 클라이언트 `round_robin` |
| 비밀값 | **K8s Secret**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, Redis 비밀번호(사용 시), FCM 서비스 계정 JSON(파일 마운트 → `FCM_CREDENTIALS_PATH`). 레포·로그에 남기지 않는다. TLS 인증서는 Secret이 아니라 ACM에서 관리한다 |
| 일반 설정 | ConfigMap/env: `SPRING_PROFILES_ACTIVE=postgres`, `AI_GRPC_TARGET`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_LISTENER_AUTO_STARTUP=true`, `SPRING_DATA_REDIS_HOST`, `FCM_ENABLED=true`, `FCM_PROJECT_ID` |
| 시간대 | 컨테이너 `TZ=Asia/Seoul` (일정·일별 집계·`LocalDateTime`이 KST 기준) |
| 클라이언트 IP | Ingress 뒤라 `server.forward-headers-strategy=native` (기기 로그인 시도 제한이 IP 기준). ConfigMap `SERVER_FORWARD_HEADERS_STRATEGY=native`로 켜고, 로컬 기본값은 `none` |
| 다중 Pod | 스케줄러·일별 배치는 Redis 락으로 한 Pod만 실행. Flyway는 DB 락으로 한 번만 실행 |
| 헬스체크 | `spring-boot-starter-actuator`. 인증 없이 `GET /actuator/health/liveness`·`/readiness`만 연다 (그 밖의 actuator는 비공개, 상세 정보 숨김). Flyway·JPA 초기화 동안은 startupProbe로 liveness를 미룬다 |
| 이미지 | BE 레포 `Dockerfile` (멀티 스테이지, `eclipse-temurin:21-jre`, 비 root uid 10001, `TZ=Asia/Seoul`) |
| 매니페스트 | `k8s/base`: ConfigMap(`wilson-backend-config`)·Deployment·Service·Ingress(ALB). 주소·호스트·ACM 인증서 ARN·FCM 프로젝트 ID는 환경별 overlay에서 패치. Secret은 커밋하지 않고 `k8s/base/secret.example.yaml`의 `kubectl create secret` 명령으로 만든다 (`wilson-backend-secret`, `wilson-fcm`) |
| AI 쪽 필요 | `round_robin`이 Pod별로 나눠 보내려면 AI 레포에 `orchestrator-server-headless`(clusterIP: None) 서비스가 있어야 한다 (결정 J9, `docs/ai-integration-contract.md` 6장) |

---

# 18. v1.2 신규 오류 코드

| code | HTTP | 의미 |
|---|---|---|
| `CS001` | 400 | 필수 동의(`SENSITIVE_INFO`) 누락 |
| `CS002` | 400 | 필수 동의는 철회할 수 없음 (5-5 삭제로 처리) |
| `G003` | 400 | 잘못되었거나 만료된 초대 코드 |
| `G004` | 429 | 초대 코드 시도 횟수 초과 |
| `G005` | 409 | 주보호자는 먼저 넘겨야 함 (연결 해제·탈퇴) |
| `G006` | 400 | 주보호자를 넘길 대상이 이 노인의 MEMBER가 아님 |
| `G007` | 404 | 보호자 연결 없음 (5-11) |
| `E003` | 503 | 초대 코드 발급 실패 |
| `SC001` | 404 | 일정 없음 |
| `SC002` | 409 | 같은 시각 일정 중복 |
| `SC003` | 404 | 선제 대화 트리거 없음 |
| `SC004` | 409 | 선제 대화 트리거 이미 사용·만료 |
| `N001` | 404 | 알림 없음 |
| `EM001` | 404 | 응급 기록 없음 |
| ~~`AI003`~~ | — | 🗑️ 폐지 (J4). 선제 첫 안부는 AI가 읽지 못하면 텍스트만 보낸다 |

---
