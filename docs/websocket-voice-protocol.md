# 음성 대화 WebSocket 프로토콜 (APP ↔ BE)

- 작성: BE
- 작성일: 2026-10-17
- 수정일: 2026-10-20 — AI 레포(`wilson_chatbot`) 구현 기준으로 오디오 형식·언어 반영 / v1.2 선제 대화·응급 프레임 추가(5장)
- 수정일: 2026-10-22 — 5장 BE 구현 완료 (선제 첫 안부 J4, 응급 J5). BE ↔ AI 계약 원본은 BE 레포 `src/main/proto` (`docs/ai-integration-contract.md`)
- 상태: 초안 (APP 검토 필요)

노인 기기(APP)와 BE 사이의 음성 턴 전송 규약입니다.
BE ↔ AI 구간은 AI 레포의 `proto/wilson/dialogue/v1/dialogue.proto`(`DialogueService/Converse`)를 따르며, BE는 AI 응답 스트림을 이 프로토콜로 앱에 중계합니다.

```
APP ──(WebSocket: 발화 오디오)──▶ BE ──(gRPC Converse)──▶ AI
APP ◀─(WebSocket: STT·음성·답변)── BE ◀─(서버 스트리밍)──── AI
```

---

## 1. 연결

```
GET /ws/v1/conversations/{conversationId}/voice
Authorization: Bearer {AccessToken}     ← 노인 기기 토큰 (POST /api/v1/auth/device-login)
```

- 대화는 먼저 `POST /api/v1/conversations`로 시작해 `conversationId`를 받아야 합니다.
- 토큰이 없거나 만료되면 핸드셰이크가 **401**로 거절됩니다. Access Token을 재발급한 뒤 다시 연결합니다.
- 한 연결에서 여러 턴을 주고받을 수 있습니다. 대화 종료(`PATCH /api/v1/conversations/{id}/finish`) 후 연결을 닫습니다.

---

## 2. APP → BE

한 턴 = 노인 발화 1건. 발화가 끝난 뒤(입력 측 실시간 STT 없음) 아래 순서로 보냅니다.

| 순서 | 프레임 | 내용 |
|---|---|---|
| 1 | Text | `{"type":"TURN_START","audioFormat":"wav"}` |
| 2 | Binary (1개 이상) | 발화 오디오 바이트. 프레임당 **최대 512KB**, 한 턴 합계 **최대 10MB** |
| 3 | Text | `{"type":"TURN_END"}` |

- 이전 턴의 `COMPLETED`/`FAILED`를 받기 전에는 새 `TURN_START`를 보내지 않습니다 (보내면 `CV003`).

### 녹음 형식 ⚠️

AI 서버(Azure Speech STT, `ko-KR`)는 **아래 두 형식만** 받습니다. 그 외(`m4a`/AAC, `mp3` 등)는 `FAILED`(`stage: STT`)로 끝납니다.

| 형식 | 컨테이너·코덱 | `audioFormat` |
|---|---|---|
| WAV | RIFF/WAVE, PCM 16bit mono (16kHz 권장) | `wav` |
| OGG | Ogg, Opus | `ogg` |

- AI 서버는 `audioFormat`을 믿지 않고 **실제 바이트 헤더**로 판별합니다. 파일 확장자만 바꾸면 실패합니다.
- 발화 하나는 **60초 이내**로 녹음합니다 (Azure 짧은 오디오 인식 한도).
- iOS/Android 기본 녹음기는 대개 m4a(AAC)를 만들므로 녹음 설정을 WAV(PCM) 또는 OGG(Opus)로 지정해야 합니다.

---

## 3. BE → APP

### 정상 흐름

| 순서 | 프레임 | 내용 |
|---|---|---|
| 1 | Text | `{"type":"TRANSCRIPT","text":"밥 먹었어"}` — 노인 발화 STT 원문 |
| 2~N | Binary | AI 답변 음성 조각. **받은 순서대로 이어 재생**합니다 (BE가 AI의 `sequence` 순서대로 전달) |
| N+1 | Text | `{"type":"COMPLETED","text":"잘하셨어요"}` — AI 답변 전체 텍스트. 턴 종료 |

**답변 음성 형식**
- 조각 하나 = 답변 **한 문장**을 합성한 **완결된 MP3 파일**입니다 (`audio-24khz-48kbitrate-mono-mp3`, 음성 `ko-KR-SunHiNeural`).
- 조각을 이어 붙이지 말고, **조각별로 순서대로 큐에 넣어 재생**하면 됩니다.
- 현재 AI는 답변 전체를 생성한 뒤 문장별로 합성합니다. 그래서 `TRANSCRIPT` 이후 첫 음성 조각까지 수 초 걸릴 수 있습니다. 이 사이에 "듣고 있어요" 같은 대기 표시를 권장합니다.

### 실패 흐름

| 프레임 | 내용 |
|---|---|
| Text | `{"type":"FAILED","stage":"TTS","recoverable":true,"text":"잘하셨어요"}` — 턴 종료 |

- `stage`: `STT` · `LLM` · `TTS` · `UNSPECIFIED` (AI가 실패를 알림) / `UNAVAILABLE` (AI 서버에 닿지 못함)
  - `STT`는 말소리가 인식되지 않았거나(무음·잡음) 녹음 형식이 맞지 않을 때입니다.
- `recoverable`: 이미 받은 음성 조각이 유효한지. `false`면 받은 조각을 버리고 **기기 내장 폴백 음성**을 재생합니다.
  - 예: "윌슨이 잠깐 딴생각을 했네요. 다시 한 번 말씀해 주시겠어요?"
- `text`: 답변 텍스트가 완성된 경우에만 포함됩니다 (없으면 필드 생략).
- `FAILED` 전에 `TRANSCRIPT`·음성 조각이 일부 왔을 수 있습니다.

> `recoverable`에 따른 세부 앱 동작(부분 재생분 폐기 등)은 AI 명세상 APP/BE 합의 대상(TBD)입니다.

### 요청 오류

턴을 시작하지 못했거나 프레임 순서가 잘못된 경우입니다. 연결은 유지되며, 턴은 시작되지 않습니다.

```json
{"type":"ERROR","code":"CV003","message":"이전 대화 응답을 처리하고 있습니다."}
```

| code | 의미 | 앱 대응 |
|---|---|---|
| `CV001` | 대화를 찾을 수 없음 | 새 대화 시작 |
| `CV002` | 이미 종료된 대화 | 새 대화 시작 |
| `CV003` | 이전 턴 처리 중 | `COMPLETED`/`FAILED` 대기 후 재시도 |
| `CV004` | 오디오가 비어 있음 | 녹음 재시도 |
| `CV005` | 프레임 순서·형식 오류 | 앱 버그 — 로그 수집 |
| `CV006` | 오디오가 10MB 초과 | 녹음 길이 제한 확인 |
| `C005` | 다른 노인의 대화 | 앱 버그 — 로그 수집 |
| `C001` | 서버 오류 | 폴백 음성 후 재시도 |

---

## 4. 예시

```
APP → {"type":"TURN_START","audioFormat":"wav"}
APP → [binary 412KB]
APP → [binary 98KB]
APP → {"type":"TURN_END"}
BE  → {"type":"TRANSCRIPT","text":"밥 먹었어"}
BE  → [binary 14KB]   ← "잘하셨어요." (mp3)
BE  → [binary 21KB]   ← "뭐 드셨어요?" (mp3)
BE  → {"type":"COMPLETED","text":"잘하셨어요. 뭐 드셨어요?"}
```

---

## 5. v1.2 추가 프레임 (BE 구현 완료, AI 쪽 대응 ⏳)

### 5-1. 선제 대화 시작 — APP → BE `PROACTIVE_START`

선제 대화(api-spec 6-2)는 앱이 FCM 푸시를 받고 `POST /api/v1/conversations/proactive`로 대화를 만든 뒤, 이 연결에서 **첫 메시지로** 보냅니다.

```
APP → {"type":"PROACTIVE_START"}
BE  → [binary]  ← "할머니, 혈압약 드실 시간이에요. 챙겨 드셨어요?" (mp3)
BE  → {"type":"COMPLETED","text":"할머니, 혈압약 드실 시간이에요. 챙겨 드셨어요?"}
APP → {"type":"TURN_START","audioFormat":"wav"}   ← 이후는 일반 턴
```

- 노인 발화가 없으므로 `TRANSCRIPT`는 오지 않습니다.
- 선제 대화(`origin: PROACTIVE`)가 아니거나 이미 말이 오간 대화에서 보내면 `ERROR CV005`.
- 문장은 푸시의 `greeting`과 같습니다 (BE가 정함, 결정 J4). BE는 AI `Speak`로 Wilson 목소리 음성을 받아 보냅니다.
- AI가 실패·미지원이면 `COMPLETED`만 옵니다 → **바이너리를 하나도 받지 못했으면** 앱은 `text`를 기기 음성(TTS)으로 읽고 일반 턴으로 진행합니다. (기존 `ERROR AI003` 응답은 폐지)
- 음성을 읽는 동안 `TURN_END`를 보내면 `ERROR CV003`입니다. `COMPLETED`를 받은 뒤 녹음을 시작하세요.

### 5-2. 응급 감지 — BE → APP `EMERGENCY`

AI가 노인 발화에서 응급 신호를 감지하면(api-spec 15장) `TRANSCRIPT` 직후에 보냅니다.

```
BE  → {"type":"TRANSCRIPT","text":"아이고 넘어졌어"}
BE  → {"type":"EMERGENCY","category":"FALL"}
BE  → [binary] ...                                   ← AI 답변은 평소처럼 이어서 옴
BE  → {"type":"COMPLETED","text":"..."}
```

- `category`: `FALL` · `PAIN` · `BREATHING` · `HELP_REQUEST` · `OTHER`
- 앱은 **기기 내장 안내 음성을 먼저 재생**하고 나서 AI 답변을 재생합니다.
  예: "보호자분께 바로 알렸어요. 무리해서 움직이지 마시고 잠시만 기다려 주세요."
- 보호자 알림은 BE가 보냅니다. 앱이 따로 할 일은 없습니다.
