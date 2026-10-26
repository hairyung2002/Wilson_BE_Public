# 인지지표 Kafka 메시지 계약 (AI → BE)

- 작성: BE
- 작성일: 2026-10-20
- 상태: **BE 확정 · AI 팀 통보** (schema_version 1)
- 근거: AI 레포 `docs/erd.md`의 `COGNITIVE_METRIC`, `turn_id` 조인 키, Kafka 멱등 설계
- BE 구현: `CognitiveMetricListener` → `CognitiveMetricService` → `cognitive_metric` 테이블 (V8)

AI **분석 워커**(대화 Pod와 분리된 별도 Pod)는 BE가 턴마다 보내는 `wilson.turn-analysis.v1` 요청(`docs/ai-integration-contract.md` 2장, 결정 J1)을 받아 분석을 마치면 아래 형식으로 Kafka에 발행합니다.
BE는 대화 응답(gRPC)과 별개로 이 토픽을 구독해 저장합니다. 대화는 분석을 기다리지 않습니다.

```
APP ─WS─▶ BE ─gRPC Converse─▶ AI ──(STT 직후 분석 트리거)──▶ 분석 Worker
                                                              │
BE ◀────────── Kafka wilson.cognitive-metrics.v1 ◀────────────┘
```

---

## 1. 토픽

| 항목 | 값 |
|---|---|
| 토픽 | `wilson.cognitive-metrics.v1` (스키마가 호환되지 않게 바뀌면 `v2` 토픽을 새로 만든다) |
| 파티션 | 3 (운영 인프라에서 생성. BE·AI 모두 자동 생성에 의존하지 않음) |
| key | `turn_id` (문자열). 같은 턴의 재발행이 같은 파티션으로 가서 순서대로 처리된다 |
| value | UTF-8 JSON (아래 2장) |
| 직렬화 | key·value 모두 `StringSerializer` |
| BE Consumer group | `wilson` |

## 2. value (JSON, snake_case)

```json
{
  "schema_version": 1,
  "turn_id": "ee84a22f-dba5-42ba-a6af-7416d2276dc3",
  "subject_id": "0ef3e83e-9b1d-4572-9b78-4221ccaf936e",
  "session_id": "15",
  "trace_id": "9ab6fd3e-b171-4786-be68-71041c4131ef",
  "analyzed_at": "2026-10-20T10:15:30.123+09:00",
  "analyzable": true,
  "metrics": {
    "morpheme_token_count": 42,
    "utterance_count": 3,
    "filler_ratio": 0.12,
    "empty_speech_ratio": 0.05,
    "content_info_unit_ratio": 0.40,
    "words_per_utterance": 14.0,
    "subsequent_onset_latency_ms": 820.0,
    "silent_pause_ratio": 0.18,
    "jitter": 0.011,
    "shimmer": 0.042,
    "cpp": 12.3
  },
  "neural": null,
  "emotion": null
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `schema_version` | int | ✅ | 현재 `1`. 다른 값이면 BE는 버린다 |
| `turn_id` | UUID 문자열 | ✅ | gRPC `AudioRequest.turn_id` 그대로. BE `message.turn_id`와 조인 |
| `subject_id` | UUID 문자열 | ✅ | gRPC `AudioRequest.target_user_id` 그대로 (노인 가명 식별자) |
| `session_id` | 문자열 | ✅ | gRPC `AudioRequest.session_id` 그대로 |
| `trace_id` | 문자열 | | gRPC `AudioRequest.trace_id`. 로그 추적용 |
| `analyzed_at` | ISO-8601 (오프셋 포함) | ✅ | 분석 완료 시각 |
| `analyzable` | bool | ✅ | 발화가 너무 짧거나 잡음뿐이면 `false` |
| `metrics` | object | `analyzable=true`면 ✅ | `false`면 `null` 또는 생략 |
| `metrics.*` | number | | 계산하지 못한 항목은 `null` 또는 생략. **음수 불가** (`cpp` 제외) |
| `neural`, `emotion` | object | | 스키마 TBD. BE는 받은 JSON을 **원문 그대로** 저장하므로 확정 전에도 자유롭게 보내도 된다 |

- `metrics` 필드명·의미는 `erd.md` `COGNITIVE_METRIC` 컬럼과 같다. `subsequent_onset_latency_ms`는 **밀리초**다.
- BE는 **모르는 필드를 무시**한다. 필드 추가는 `schema_version`을 올리지 않아도 된다.
- 필드 이름 변경·삭제·타입 변경 같은 호환되지 않는 변경만 `schema_version`과 토픽 버전을 올린다.
- 로그·메시지에 발화 원문이나 실명을 넣지 않는다 (수치와 식별자만).

## 3. 전달 보장과 BE 처리 규칙

| 상황 | BE 동작 |
|---|---|
| 정상 | `cognitive_metric`에 저장 후 오프셋 커밋 |
| 같은 `turn_id` 재수신 | 저장하지 않고 넘어감 (`turn_id` UNIQUE). **AI는 at-least-once로 재발행해도 된다** |
| JSON 깨짐·필수 필드 누락·UUID 형식 오류·음수 지표·`analyzable=true`인데 `metrics` 없음 | 경고 로그(파티션·오프셋·필드명) 후 **버림**. 다시 읽어도 실패하므로 재시도하지 않음 |
| 지원하지 않는 `schema_version` | 경고 로그 후 버림 |
| BE DB 장애 등 일시 오류 | 1초→2배→최대 30초 간격으로 10분간 재시도 (그동안 해당 파티션 대기) |

- 메시지가 버려지면 BE 로그에서 `Invalid cognitive metric discarded` / `Malformed cognitive metric discarded`로 찾을 수 있다.
- BE는 처음 붙을 때 토픽의 처음부터 읽는다 (`auto-offset-reset: earliest`).

## 4. AI 팀 작업 요청

1. orchestrator `MetricsPublisher.publish_analysis_result`를 이 계약대로 구현 (`aiokafka` 등)
2. 발행 시 key = `turn_id`, acks=all 권장
3. 분석 Worker는 대화 응답 경로(gRPC 스트림)와 **다른 프로세스**에서 실행 (분석 부하가 대화를 끊지 않도록)
