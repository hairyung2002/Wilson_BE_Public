# AI 연동 계약 (BE 확정)

- 확정일: 2026-10-22 · 결정: `docs/decisions.md` J1~J9
- 이 문서와 BE 레포의 `src/main/proto`가 **계약 원본**이다. AI 레포는 proto를 그대로 복사해 쓰고, 바꿀 일이 있으면 BE와 먼저 합의한다.
- 이 문서로 `docs/ai-review-request.md`의 1번(선제 첫 발화)·2번(`EndSession`)·5번(요약)·8번(응급)·9번(`DeleteSubject`) 제안을 대체한다.

## 0. 원칙

| 구간 | 방식 | 담는 것 |
|---|---|---|
| 실시간 대화 | gRPC `DialogueService` (내부 h2c) | 발화 → STT → 답변 음성 스트림, 응급 감지, 선제 안부 읽기 |
| 무거운 처리 | Kafka (대화를 기다리게 하지 않음) | 턴 분석(인지지표), 대화 종료 후 RAG 저장·요약, 노인 파기 |

- **분석·임베딩·요약은 대화 Pod(오케스트레이터)에서 하지 않는다.** 별도 워커 Pod가 Kafka를 소비한다 (대화가 무거운 연산에 막히지 않게).
- AI로 가는 식별자는 가명 `target_user_id`(= BE `elderly.subject_id`, UUID)뿐이다. 실명·케어 메모는 보내지 않는다.

### Kafka 토픽

| 토픽 | 방향 | key | value | 보관 |
|---|---|---|---|---|
| `wilson.turn-analysis.v1` | BE → AI | `turn_id` | Protobuf `TurnAnalysisRequested` | 24시간 (음성 포함) |
| `wilson.conversation-ended.v1` | BE → AI | `session_id` | Protobuf `ConversationEnded` | 24시간 (원문 포함) |
| `wilson.subject-erasure.v1` | BE → AI | `target_user_id` | Protobuf `SubjectErasureRequested` | 7일 |
| `wilson.cognitive-metrics.v1` | AI → BE | `turn_id` | JSON (`docs/kafka-cognitive-metrics.md`, 변경 없음) | 기존 |
| `wilson.conversation-summaries.v1` | AI → BE | `session_id` | JSON (4장) | 7일 |

- BE → AI 메시지 proto: BE 레포 `src/main/proto/wilson/events/v1/events.proto` (package `wilson.events.v1`).
- 모든 소비자는 **같은 메시지를 두 번 받아도 결과가 같아야 한다** (at-least-once).
- `turn-analysis`는 음성(BE 음성 턴 한도 10MB)을 담으므로 토픽·프로듀서 최대 메시지 크기를 **12MB**로 둔다. AI 워커 consumer도 이 크기를 받을 수 있어야 한다.
- BE → AI 토픽 3개는 BE가 시작할 때 만든다(보관 기간·메시지 크기 포함, `KAFKA_TOPIC_AUTO_CREATE`). AI → BE 토픽은 AI 쪽이 만든다.

---

## 1. gRPC `DialogueService` 추가분

`dialogue.proto`(package `wilson.dialogue.v1`)에 아래를 추가한다. 기존 필드 번호는 바꾸지 않는다.

```protobuf
message AudioRequest {
  // 1~7 기존과 동일
  repeated HistoryTurn history = 8;   // J6: 같은 대화의 직전 턴들(오래된 순, 최대 10턴)
  string address_term          = 9;   // J7: 호칭 "할머니" | "할아버지" | "어르신"
}

message HistoryTurn {
  string turn_id           = 1;
  string stt_text          = 2;  // 노인 발화. 선제 안부 턴이면 빈 값
  string llm_response_text = 3;  // AI 발화
}

message StreamResponse {
  oneof payload {
    MetadataFrame   metadata    = 1;
    AudioChunk      audio_chunk = 2;
    CompletionFrame completion  = 3;
    ErrorFrame      error       = 4;
    EmergencyFrame  emergency   = 5;  // J5
  }
}

message EmergencyFrame {
  EmergencyCategory category = 1;
}

enum EmergencyCategory {
  EMERGENCY_CATEGORY_UNSPECIFIED  = 0;
  EMERGENCY_CATEGORY_FALL         = 1;  // 낙상
  EMERGENCY_CATEGORY_PAIN         = 2;  // 통증
  EMERGENCY_CATEGORY_BREATHING    = 3;  // 호흡
  EMERGENCY_CATEGORY_HELP_REQUEST = 4;  // 도움 요청
  EMERGENCY_CATEGORY_OTHER        = 5;
}

message SpeakRequest {
  string trace_id       = 1;
  string target_user_id = 2;
  string session_id     = 3;
  string turn_id        = 4;
  string text           = 5;  // BE가 정한 문장. AI는 바꾸지 않고 읽기만 한다
  string language_code  = 6;
}

service DialogueService {
  rpc Converse(AudioRequest) returns (stream StreamResponse);
  rpc Speak(SpeakRequest)    returns (stream StreamResponse);  // J4
}
```

### 1-1. `Converse` — 맥락·호칭 (J6·J7)
- `history`를 **LLM 입력에 반드시 넣는다.** BE는 Redis에 대화 맥락을 두고 입력마다 만료를 연장하므로, AI가 이를 쓰지 않으면 긴 침묵 뒤 맥락이 끊긴다. `llm.proto` `GenerateRequest`에 history 필드를 추가하는 방식을 권장한다.
- `address_term`은 답변에서 어르신을 부르는 말로 쓴다. 비어 있으면 "어르신".

### 1-2. `Speak` — 선제 안부 읽기 (J4)
- 프레임 순서: `AudioChunk`(sequence 1부터) … → `CompletionFrame{full_llm_response_text = 요청 text}`. `MetadataFrame`은 보내지 않는다.
- TTS 실패는 `ErrorFrame{error_stage = TTS, recoverable = 이미 보낸 청크가 있는지}`.
- LLM·RAG를 거치지 않는다 (문장은 BE가 정함).
- BE는 `UNIMPLEMENTED`나 실패를 받으면 앱에 텍스트만 보내고, 앱이 기기 음성으로 읽는다.
  `UNIMPLEMENTED`는 재시도하지 않고 Circuit Breaker 실패로도 세지 않는다 (AI가 `Speak`를 만들기 전에도 `Converse`에 영향 없음).
- BE는 `CompletionFrame`의 텍스트 대신 자신이 보낸 문장을 앱에 보낸다.

### 1-3. 응급 (J5)
- `MetadataFrame` 바로 다음, 감지됐을 때만 보낸다. 이후 답변(`AudioChunk` → `CompletionFrame`)은 평소처럼 이어서 보낸다.
- 감지는 대화 경로에서 STT 텍스트로 한다 (분석 워커는 늦어서 쓰지 않는다).

---

## 2. 턴 분석 요청 — `wilson.turn-analysis.v1` (J1)

BE가 **AI 답변이 끝난 뒤**(정상 완료 또는 실패) 노인 발화가 있었던 턴마다 1건 발행한다.

```protobuf
message TurnAnalysisRequested {
  string trace_id       = 1;
  string turn_id        = 2;  // 지표 메시지의 turn_id로 그대로 돌려준다
  string target_user_id = 3;
  string session_id     = 4;
  bytes  audio_payload  = 5;  // Converse에 보낸 것과 같은 노인 발화 원본
  string audio_format   = 6;  // "wav" | "ogg"
  string stt_text       = 7;
  string llm_response_text = 8;  // 답변 생성에 실패했으면 빈 값
  google.protobuf.Timestamp occurred_at = 9;
}
```

- AI 분석 워커는 지표를 계산해 `wilson.cognitive-metrics.v1`로 발행한다 (기존 계약 그대로).
- 오케스트레이터의 `MetricsPublisher`(대화 경로에서 발행)는 쓰지 않는다.
- 앱은 AI 음성 재생이 끝나면 곧바로 녹음을 시작한다. 그래서 오디오 앞부분의 무음 길이가 `subsequent_onset_latency_ms`(대답까지 걸린 시간)의 근거가 된다.

---

## 3. 대화 종료 — `wilson.conversation-ended.v1` (J2)

BE가 대화를 종료할 때 1건 발행한다: 종료 API, 새 대화 시작 시 이전 대화 자동 종료. 턴이 하나도 없으면 발행하지 않는다.
노인 삭제 때는 보내지 않는다 (곧 파기할 원문을 AI로 보내지 않는다. 대신 5장 파기 요청이 나간다).
발행은 종료가 커밋된 뒤에 하고, 실패해도 다시 보내지 않는다 (요약이 없으면 보호자 화면은 `summary: null`).

```protobuf
message ConversationEnded {
  string trace_id       = 1;
  string target_user_id = 2;
  string session_id     = 3;
  google.protobuf.Timestamp started_at = 4;
  google.protobuf.Timestamp ended_at   = 5;
  repeated ConversationTurn turns      = 6;  // 오래된 순. 선제 안부 턴은 stt_text가 빈 값
}

message ConversationTurn {
  string turn_id           = 1;
  string stt_text          = 2;
  string llm_response_text = 3;
}
```

AI 워커가 할 일:
1. `rag.proto` `StoreConversation`으로 `elder` 컬렉션에 저장한다. 문서 ID가 `session:turn:role`로 정해져 있으므로 다시 받아도 같은 결과다.
2. 보호자용 요약을 만들어 4장 토픽으로 발행한다.

---

## 4. 대화 요약 — `wilson.conversation-summaries.v1` (J2, AI → BE, JSON)

```json
{
  "schema_version": 1,
  "session_id": "15",
  "target_user_id": "3f9c1c9e-6d2a-4b8e-9a51-2a7c0f1b8d10",
  "summary": "점심으로 된장찌개를 드셨고, 오후에 산책을 다녀오실 계획을 이야기하셨어요.",
  "generated_at": "2026-10-22T03:12:45Z"
}
```

요약 작성 규칙 (보호자에게 그대로 보인다):
- 1~2문장, 200자 이내, 존댓말
- 대화 원문을 인용하지 않는다. 실명을 쓰지 않는다
- 어르신의 말실수·반복·기억 오류를 지적하거나 평가하지 않는다
- "치매·진단·위험·질환·이상" 같은 의료 표현을 쓰지 않는다 (api-spec 12-4)

BE 처리:
- 대화가 없거나 삭제됐으면 버린다.
- `target_user_id`가 그 노인의 현재 `subject_id`와 다르면 버린다 (삭제 후 도착한 요약).
- 200자를 넘거나 필수 필드가 없거나 `session_id`가 숫자가 아니면 버린다.
- "치매·진단·질환"이 들어 있으면 보호자에게 보이지 않도록 버린다 (나머지 규칙은 AI가 지킨다).
- 같은 대화에 다시 오면 덮어쓴다.

---

## 5. 노인 파기 — `wilson.subject-erasure.v1` (J3)

BE가 노인을 삭제(api-spec 5-5)하면, 바꾸기 전의 `subject_id`로 1건 발행한다. 요청은 삭제와 같은 트랜잭션에 DB(`subject_erasure_outbox`)로 남기고,
1분마다 브로커가 받을 때까지 다시 보낸다. 그래서 같은 요청이 두 번 올 수 있다.

```protobuf
message SubjectErasureRequested {
  string trace_id       = 1;
  string target_user_id = 2;  // 파기할 subject_id
  google.protobuf.Timestamp requested_at = 3;
}
```

AI 워커가 할 일:
- `elder` 컬렉션에서 그 `target_user_id`의 문서를 모두 지운다. 이미 지워졌어도 성공으로 본다.
- 파기한 `target_user_id`를 **최소 24시간** 기억한다. 그 사이에 늦게 도착한 같은 subject의 `conversation-ended`는 저장하지 않고 버린다 (토픽 간 순서는 보장되지 않음).
- 인지지표는 BE가 연구 보관 동의 여부에 따라 처리한다. AI 쪽 할 일이 아니다.

---

## 6. K8s (J9)

AI 레포 `services/orchestrator-server/k8s/base`에 Headless Service를 추가한다. BE는 `AI_GRPC_TARGET=dns:///orchestrator-server-headless:50051`, `round_robin`으로 Pod마다 나눠 보낸다.

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

---

## 7. AI 팀 작업 목록

| # | 작업 | 위치 |
|---|---|---|
| 1 | `dialogue.proto`를 1장대로 갱신 (`history`, `address_term`, `EmergencyFrame`, `Speak`) | proto |
| 2 | `Converse`: history·호칭을 LLM 입력에 반영, 응급 감지 후 `EmergencyFrame` 전송 | orchestrator |
| 3 | `Speak`: 받은 문장을 TTS로 스트리밍 | orchestrator |
| 4 | 분석 워커 Pod: `turn-analysis` 소비 → 지표 계산 → `cognitive-metrics` 발행 | 새 워커 |
| 5 | 대화 종료 워커: `conversation-ended` 소비 → `StoreConversation` + 요약 → `conversation-summaries` 발행 | 새 워커 |
| 6 | 파기 워커: `subject-erasure` 소비 → `elder` 문서 삭제 (+ 24시간 tombstone) | 새 워커 + rag-server 삭제 기능 |
| 7 | `orchestrator-server-headless` Service 추가 | k8s |
