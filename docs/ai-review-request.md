# AI 서버 연동 검토 요청 (BE → AI)

- 작성: BE
- 작성일: 2026-10-16
- 수정일: 2026-10-22 — 답변 대기 항목을 **BE가 확정**했습니다 → `docs/ai-integration-contract.md` (결정 J1~J9)
- 수정일: 2026-10-20 — 기준 문서를 Notion에서 **AI 레포로 변경**, BE 통보 사항(0-1 대화 맥락, 0-2 로드밸런싱, 3 Kafka 계약) 추가, BE 결정 반영(1·5·7) 및 신규 요청(8 응급 프레임, 9 임베딩 삭제)
- 기준: AI 레포 `wilson_chatbot` `main` (`ba02914`, TTS Azure 연동까지)
  - `proto/wilson/dialogue/v1/dialogue.proto`, `proto/wilson/rag/v1/rag.proto`
  - `docs/architecture.md`, `docs/erd.md`, `docs/research-retention.md`
  - `services/orchestrator-server` (pipeline, STT/TTS 어댑터, settings)

> **2026-10-22 BE 확정:** 아래 1·2·4·5·8·9번은 더 이상 질문이 아니라 `docs/ai-integration-contract.md`의 **확정 계약**으로 대체됐습니다. AI 팀은 그 문서 7장 작업 목록을 기준으로 맞춰 주세요.

BE는 AI 측 계약(`wilson.dialogue.v1.DialogueService/Converse`)을 그대로 따라 구현했습니다 (proto 와이어 포맷 동일 확인).
아래는 **BE가 AI 레포에 맞춰 바꾼 것**과 **AI 팀 답변이 필요한 것**입니다.

---

## 0. BE가 AI 레포 기준으로 맞춘 것 (확인만 부탁드립니다)

| 항목 | 이전 (Notion 기준) | 변경 후 (AI 레포 기준) |
|---|---|---|
| `language_code` | `ja-JP` | **`ko-KR`** (orchestrator `settings.language_code`와 동일) |
| `target_user_id` | 노인 PK (숫자 문자열) | **노인 가명 식별자 `subject_id` (UUID v4)** — `erd.md`·`research-retention.md`의 "AI엔 가명 subject_id만" 반영 |
| `session_id` | BE 대화 ID (숫자 문자열) | 동일. 전역 유일하므로 RAG 문서 ID(`session:turn:role`) 충돌 없음 |
| `turn_id`, `trace_id` | BE가 UUID v4 생성 | 동일 |
| 입력 오디오 | 앱 문서에 `m4a` 예시 | **WAV(PCM) / OGG-OPUS만** 앱에 안내 (STT `_content_type` 기준) |
| 출력 오디오 | 미정 | 문장 단위 완결 MP3(`audio-24khz-48kbitrate-mono-mp3`)를 앱에 순서대로 중계 |
| 첫 프레임 타임아웃 | 10초 | **20초** — AI STT httpx 타임아웃(15초)보다 길게. 짧으면 AI가 처리 중일 때 BE가 재시도해 STT가 중복 호출됨 |
| 스트림 deadline | 30초 | **60초** — STT + RAG + LLM 전체 생성 + 문장별 TTS가 순차라 30초는 빠듯함 |
| 재시도 | 첫 프레임 수신 전 실패만 최대 3회 | 동일 (`ErrorFrame`은 프레임이므로 재시도하지 않음) |
| `ErrorFrame` 사용 필드 | `error_stage`, `recoverable`, `full_llm_response_text` | 동일. TTS 실패 시 받은 텍스트는 대화 이력에 저장 |
| gRPC 대상·분산 | `host:port` 단일 연결 | **`dns:///<headless-service>:50051` + `round_robin`** (0-2) |

---

## 0-1. 📢 BE 통보: 대화 맥락(`history`) 필드 추가

AI 서버는 무상태라, 현재 LLM은 **같은 대화의 직전 턴도 모르는 상태**로 답합니다 (pipeline이 이번 발화 + RAG 결과만 넘김).
BE가 대화 맥락을 들고 있다가 매 요청에 실어 보내도록 `AudioRequest`에 필드를 추가했습니다.

```protobuf
message AudioRequest {
  ...
  repeated HistoryTurn history = 8;  // 같은 대화의 직전 턴들(오래된 순, 최대 10턴). 첫 턴이면 비어 있음
}

message HistoryTurn {               // 필드명은 rag.proto ConversationTurn과 동일
  string turn_id           = 1;
  string stt_text          = 2;    // 노인 발화
  string llm_response_text = 3;    // AI 답변
}
```

- **호환성:** 새 필드라 AI가 지금 스텁을 다시 만들지 않아도 기존 동작은 그대로입니다 (BE ↔ AI 실제 서버 연동으로 확인).
- **BE 보관 방식:**
  - 대화별 Redis 리스트에 최근 10턴을 둡니다.
  - **발화마다 TTL(30분)을 다시 늘리고**(Sliding Expiration), 만료되면 DB 대화 이력에서 다시 채웁니다.
  - 대화가 종료되면 삭제합니다.
- **AI 작업 요청:**
  1. `dialogue.proto`에 위 필드를 반영하고 스텁을 다시 생성합니다.
  2. `pipeline.converse`에서 `history`를 LLM `Generate` 요청의 대화 이력(멀티턴 메시지)으로 넘깁니다.
  3. `llm.proto`에 이력 필드가 없으면 함께 추가합니다.

## 0-2. 📢 BE 통보: gRPC 클라이언트 로드밸런싱

gRPC는 HTTP/2 연결 하나를 재사용하므로, ClusterIP(L4) Service 뒤에 orchestrator Pod를 여러 개 띄워도 BE 요청이 Pod 하나로 몰립니다.
BE는 `dns:///` 이름 해석으로 Pod 주소를 모두 받아 요청마다 번갈아 보내도록(`round_robin`) 바꿨습니다. 테스트로 `pick_first`(6:0) → `round_robin`(3:3) 분산을 확인했습니다.

**AI 작업 요청 (K8s):**
1. orchestrator에 **headless Service** 추가 (`clusterIP: None`, 이름 예: `orchestrator-server-headless`). 기존 ClusterIP Service는 그대로 둬도 됩니다.
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: orchestrator-server-headless
   spec:
     clusterIP: None
     selector:
       app.kubernetes.io/name: orchestrator-server
     ports:
       - name: grpc
         port: 50051
         targetPort: grpc
         appProtocol: grpc
   ```
2. orchestrator gRPC 서버에 **연결 최대 수명** 설정을 추가합니다.
   - 예: `grpc.max_connection_age_ms=300000`, `grpc.max_connection_age_grace_ms=30000`
   - 클라이언트는 연결이 끊길 때만 DNS를 다시 조회하므로, 이 설정이 없으면 Pod를 늘려도 기존 연결이 새 Pod로 퍼지지 않습니다.
3. BE 환경변수: `AI_GRPC_TARGET=dns:///orchestrator-server-headless.<namespace>.svc.cluster.local:50051`

---

## 1. 선제적 대화(AI가 먼저 말 걸기) RPC — 🔴 차단

> **[대체됨 2026-10-22]** 첫 안부 문장은 BE가 정하고, AI는 `Speak` RPC로 읽기만 합니다 → 계약 1-2장 (결정 J4)

`Converse`는 노인 발화 오디오가 있어야만 응답을 만듭니다. 윌슨 1번 원칙(AI가 먼저 안부를 묻는다)과 BE 명세 6-2(식사·복약·산책 시점에 대화 시작)를 구현하려면 **오디오 없이 상황(트리거)으로 첫 발화를 만드는 RPC**가 필요합니다.

```protobuf
message ProactiveRequest {
  string trace_id       = 1;
  string target_user_id = 2;  // subject_id
  string session_id     = 3;
  string turn_id        = 4;
  string trigger_type   = 5;  // MEDICATION, MEAL, WALK, DAILY
  string language_code  = 6;  // ko-KR
  string label          = 7;  // 보호자·노인이 붙인 일정 이름 (예: "혈압약"). 없으면 빈 값. 실명 등 개인정보 아님
  repeated HistoryTurn history = 8;  // 같은 대화의 이전 턴 (선제 대화는 보통 비어 있음)
}

service DialogueService {
  rpc Converse(AudioRequest) returns (stream StreamResponse);
  rpc StartProactive(ProactiveRequest) returns (stream StreamResponse);  // MetadataFrame 생략, AudioChunk → CompletionFrame
}
```

응답은 기존 `StreamResponse`를 재사용하면 BE·APP의 스트림 처리 로직을 그대로 쓸 수 있습니다.

**BE 결정 반영 (2026-10-20):**
- 일정 종류는 `MEDICATION`·`MEAL`·`WALK`·`DAILY` 4가지입니다.
- 선제 대화의 흐름은 이렇습니다. BE 스케줄러가 FCM 푸시로 노인 기기를 깨우면, 앱이 대화를 만들고 음성 WebSocket에서 `PROACTIVE_START`를 보냅니다. **이 시점에 BE가 `StartProactive`를 호출**합니다 (api-spec 6-2).
- RPC가 생기기 전까지 BE는 푸시·대화 생성·무응답 알림을 먼저 구현하고, 첫 발화 요청에는 "지원 안 함" 오류를 돌려줍니다.

---

## 2. 대화 종료 시 RAG 저장(`StoreConversation`) 트리거 경로 — 🔴 차단

> **[대체됨 2026-10-22]** 동기 `EndSession` 대신 Kafka `wilson.conversation-ended.v1`을 AI 워커가 소비해 `StoreConversation`을 호출합니다 → 계약 3장 (결정 J2)

### 현황
- `rag.proto`의 `StoreConversation` 주석: "오케스트레이터가 **세션 종료 시** 그 세션의 턴들을 담아 호출한다."
- 그런데 오케스트레이터는 무상태이고, `dialogue.proto`에는 **세션 종료를 알리는 RPC가 없습니다.** 현재 오케스트레이터 코드에도 호출부가 없습니다.
- 이대로면 `elder` 컬렉션에 대화가 쌓이지 않아 RAG 개인 컨텍스트(이전 대화 기억)가 동작하지 않습니다.

### 제안
대화 원본(턴 텍스트)은 BE에 있으므로, **BE가 대화 종료 시 턴 목록을 담아 호출**하는 RPC를 제안합니다.

```protobuf
message EndSessionRequest {
  string trace_id       = 1;
  string target_user_id = 2;  // subject_id
  string session_id     = 3;
  repeated SessionTurn turns = 4;
}
message SessionTurn {
  string turn_id           = 1;
  string stt_text          = 2;
  string llm_response_text = 3;
}
message EndSessionResponse {
  uint32 stored_count = 1;
}

service DialogueService {
  rpc EndSession(EndSessionRequest) returns (EndSessionResponse);  // 오케스트레이터 → RAG StoreConversation 위임
}
```

- BE 호출 시점: 대화 종료 API, 새 대화 시작 시 이전 대화 자동 종료.
- 문서 ID가 `session:turn:role`로 결정적이라 BE가 재시도해도 멱등이라는 점을 확인 부탁드립니다.

---

## 3. 인지지표 Kafka 메시지 계약 — ✅ BE 확정·통보

**`docs/kafka-cognitive-metrics.md`** 로 BE가 확정했고, BE Consumer·저장까지 구현을 마쳤습니다 (실제 Kafka 브로커로 검증).

- **토픽·키:** `wilson.cognitive-metrics.v1`, key = `turn_id`, value = JSON(snake_case, `erd.md` 컬럼명 그대로)
- **발행 단위:** 턴마다 1건. at-least-once로 발행해도 BE가 `turn_id`로 중복을 걸러냅니다.
- **`neural`, `emotion`:** 스키마 확정 전에도 JSON 그대로 보내면 원문으로 저장됩니다.
- **AI 작업:** orchestrator `MetricsPublisher`를 이 계약대로 구현하고, 분석 Worker를 대화 응답 경로와 다른 프로세스로 분리합니다.

---

## 4. 개인화 정보 전달 — 🟠 확인 필요

> **[확정 2026-10-22]** 1번 방향, 단 비식별 호칭만: `AudioRequest.address_term`(9번) → 계약 1-1장 (결정 J7)

- 시스템 프롬프트는 orchestrator `settings.system_prompt`로 고정이고, RAG `elder` 컬렉션은 대화 기록만 담습니다.
- 노인 호칭(이름), 보호자가 입력한 케어 메모, 오늘 일정(복약 시간 등)을 AI가 받을 경로가 없습니다.
- 다만 이름은 개인정보라 "AI엔 가명만" 원칙과 부딪칩니다. 아래 중 방향을 정해 주세요.
  1. `AudioRequest`에 호칭(예: "할머니")·케어 메모 등 **비식별 컨텍스트** 필드 추가
  2. 개인화하지 않음 (현 상태 유지)

---

## 5. 보호자용 요약(`guardian_insight`) 생성 주체 — 🔴 우선순위 상향

> **[대체됨 2026-10-22]** 요약은 대화 종료 이벤트를 받은 AI 워커가 만들어 Kafka `wilson.conversation-summaries.v1`로 돌려줍니다 → 계약 4장 (결정 J2)

- `erd.md`의 `GUARDIAN_INSIGHT`는 "elder→guardian 가공본(원문 노출 금지)"입니다.
- **BE 결정 (2026-10-20):** 보호자는 기본적으로 **요약·지표만** 봅니다. 원문은 노인(또는 대리인)이 원문 공유에 동의했을 때만 공개합니다 (api-spec 6-9·6-10).
  따라서 요약이 없으면 동의하지 않은 보호자는 **대화 내용을 전혀 알 수 없습니다.**
- 요청: 대화 종료 시 요약을 AI가 만들어 주세요. 채널은 2번 `EndSession` 응답에 `summary` 필드를 넣는 방식을 제안합니다.
  ```protobuf
  message EndSessionResponse {
    uint32 stored_count = 1;
    string summary      = 2;  // 보호자용 1~2문장 요약. 원문 인용·실명 없이, 교정·평가하는 표현 없이
  }
  ```
- 그전까지 BE는 대화 목록을 `summary: null`로 제공합니다.

---

## 6. 응답 지연 — ⚪ 참고

- 현재 TTS는 LLM 응답 **전체 생성 후** 문장별로 순차 합성합니다. `MetadataFrame` 후 첫 `AudioChunk`까지 LLM 전체 생성 시간이 그대로 대기 시간이 됩니다.
- README의 "단계 간 오버랩(후속)"이 적용되면 체감 지연이 크게 줄 것으로 보여 우선순위를 높여 주시면 좋겠습니다.
- 앱 녹음 기본 형식이 m4a(AAC)인 경우가 많습니다. m4a 지원(README "후속")이 들어오면 앱 제약이 풀립니다.

---

## 7. `erd.md` 대비 BE 결정 — 공유 (2026-10-20 확정, `docs/decisions.md`)

`erd.md`는 BE 데이터 모델 제안이라, BE에서 확정한 내용을 공유합니다.

| 항목 | `erd.md` | BE 결정 |
|---|---|---|
| 가명 키 | `research_subject` 테이블 분리, 철회 시 링크 절단 | `elderly.subject_id` 컬럼. 노인 삭제 시 이 값을 새 무작위 값으로 바꿔 **링크를 끊고**, 연구 보관 동의가 있을 때만 지표를 남김 |
| 노인 로그인 | `elder.login_id`/`pw_hash` | **기기 연결 코드** 유지 (디지털 취약 어르신 기준) |
| 보호자-노인 관계 | `role OWNER/MEMBER`, `status PENDING/ACTIVE`, OWNER 승인 | `OWNER/MEMBER` 채택. 승인 대기 대신 **OWNER가 발급한 노인별 초대 코드**로 연결. 마지막 OWNER는 넘기기 전 해제·탈퇴 불가 |
| 보호자 열람 범위 | `guardian_insight`(원문 노출 금지) | **요약·지표 기본**, 원문은 원문 공유 동의 시만 (5번) |
| 동의 | `consent` (대리동의·연구보관 동의) | 채택: 민감정보(필수)·연구 보관·원문 공유. 대리 동의는 법적 근거 기록. 법률 자문 전까지 기록만 |
| 대화 단위 | `session`/`turn`(UUID, 턴당 1행) | 유지: `conversation`(BIGINT) / `message`(발화·답변 각 1행, `turn_id`로 묶음) |
| 판정 | `elder_baseline`, `assessment_flag` | 채택: 처음 14일 기준값, 2σ 5일 연속 판정, 매일 03:00 배치 (api-spec 12장) |
| 턴 텍스트 암호화 | at-rest 암호화 | MVP는 볼륨/TDE로 대응 |

---

## 8. 응급 감지 프레임 — 🟠 요청 (BE 결정 G1)

> **[확정 2026-10-22]** 아래 제안 그대로 확정 계약입니다 → 계약 1-3장 (결정 J5)

BE는 응급 상황을 **AI가 발화에서 감지**하고 응답 스트림으로 알려 주는 방식으로 정했습니다 (RAG 응급 가드레일 TBD와 연결).
감지되면 BE가 보호자 전원에게 즉시 알리고 노인 기기에 안내 음성을 재생합니다 (api-spec 15장).

```protobuf
message StreamResponse {
  oneof payload {
    MetadataFrame   metadata    = 1;
    AudioChunk      audio_chunk = 2;
    CompletionFrame completion  = 3;
    ErrorFrame      error       = 4;
    EmergencyFrame  emergency   = 5;  // [제안] MetadataFrame 직후, 감지됐을 때만
  }
}

message EmergencyFrame {
  EmergencyCategory category = 1;
}

enum EmergencyCategory {
  EMERGENCY_CATEGORY_UNSPECIFIED = 0;
  EMERGENCY_CATEGORY_FALL         = 1;  // 낙상
  EMERGENCY_CATEGORY_PAIN         = 2;  // 통증
  EMERGENCY_CATEGORY_BREATHING    = 3;  // 호흡
  EMERGENCY_CATEGORY_HELP_REQUEST = 4;  // 도움 요청
  EMERGENCY_CATEGORY_OTHER        = 5;
}
```

- 응급이어도 스트림은 평소처럼 이어서 답변(AudioChunk → CompletionFrame)을 보내 주세요. 응급일 때의 답변 톤은 AI 쪽 프롬프트 몫입니다.
- 오탐 비용(보호자 불안)과 미탐 비용(안전) 사이의 감지 기준은 AI 팀이 정하고 공유해 주세요.

---

## 9. 노인 삭제 시 RAG 개인 임베딩 삭제 — 🟠 요청 (BE 결정 B5)

> **[대체됨 2026-10-22]** 동기 `DeleteSubject` 대신 Kafka `wilson.subject-erasure.v1`을 AI 워커가 소비합니다 → 계약 5장 (결정 J3)

노인이 삭제되면 BE는 실명·대화 원문을 파기합니다. AI 쪽 `elder` 컬렉션의 임베딩도 대화 원문에서 나온 것이라 함께 지워야 합니다 (`research-retention.md` 3장).

```protobuf
message DeleteSubjectRequest {
  string trace_id       = 1;
  string target_user_id = 2;  // 삭제할 노인의 subject_id
}
message DeleteSubjectResponse {
  uint32 deleted_count = 1;
}

service DialogueService {
  rpc DeleteSubject(DeleteSubjectRequest) returns (DeleteSubjectResponse);  // 오케스트레이터 → RAG elder 컬렉션 삭제 위임
}
```

- 같은 `target_user_id`로 다시 불러도 결과가 같아야 합니다 (BE 재시도 대비, 멱등).
- BE는 삭제 직후 `subject_id`를 새 값으로 바꾸므로, 이후 그 노인의 데이터는 새 키로만 쌓입니다.
