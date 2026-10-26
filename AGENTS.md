# AGENTS.md

# Wilson Backend Development Guide

이 프로젝트는 치매 당사자와 보호자를 동시에 케어하는 AI 심리케어 서비스 **Wilson(윌슨)** 의 Spring Boot Backend이다.

모든 개발, 코드 생성, 리팩토링, 테스트 작성은 본 문서를 절대적으로 준수한다.

---

# 1. Project Philosophy

Wilson은 단순한 챗봇이 아니다.

사용자의 존엄성을 지키고,
관계를 형성하며,
심리적 안정감을 제공하는 AI Care Partner이다.

따라서 모든 비즈니스 로직과 AI 연동은 아래 철학을 반드시 따른다.

## 1. 선제적 다가감

사용자가 먼저 말을 걸기 전에 AI가 먼저 안부를 묻는다.

예)

- 식사는 하셨나요?
- 오늘 산책은 다녀오셨나요?
- 오늘은 기분이 어떠세요?

## 2. 인격체 기반 관계 형성

Wilson은 의료기기가 아니다.

노인에게는 손주처럼

보호자에게는 감정을 털어놓을 수 있는 상담 파트너처럼 동작한다.

## 3. 존엄성 유지

노인의

- 말실수
- 반복 질문
- 기억 오류

를 절대로 교정하거나 지적하지 않는다.

올바른 정보를 자연스럽게 다시 전달하며
사용자의 자존감을 최우선으로 보호한다.

---

# 2. Domain Background

AI가 기능을 구현할 때 반드시 고려해야 하는 사회적 배경이다.

- 초고령사회
- 독거노인 증가
- 보호자 번아웃
- 치매 조기 발견
- 디지털 취약계층
- 비의료적 심리 케어
- 가족 관계 회복

모든 기능은 위 문제를 해결하기 위한 방향으로 설계한다.

---

# 3. Tech Stack

Language

- Java 21

Framework

- Spring Boot 4.1.x
- Spring Data JPA
- Spring Security

Database

- PostgreSQL (운영), H2 (로컬/테스트)
- Redis

Communication

- gRPC (AI 서버 동기 호출)
- Kafka (비동기 이벤트/분석 파이프라인)

Build

- Gradle

Testing

- JUnit5
- Mockito
- AssertJ
- MockMvc

---

# 4. Development Principles

우리는 아래 원칙을 따른다.

- TDD
- SOLID
- Clean Code
- Clean Architecture
- Domain Driven Design (DDD Lite)
- RESTful API
- Fail Fast
- Explicit over Implicit
- YAGNI
- KISS

우선순위는 아래와 같다.

정확성

>

안정성

>

테스트

>

확장성

>

성능

>

개발 속도

---

# 5. TDD Workflow

모든 기능은 반드시 아래 순서를 따른다.

Red

↓

Green

↓

Refactor

절대로 테스트 없이 프로덕션 코드를 작성하지 않는다.

---

# 6. Feature Development Workflow

Codex는 반드시 아래 순서를 따른다.

1. Requirement 분석

2. Domain 설계

3. Entity 설계

4. Repository 작성

5. Service Test 작성

6. Service 구현

7. Controller Test 작성

8. Controller 구현

9. Refactoring

10. API 검토

절대로 여러 단계를 동시에 구현하지 않는다.

---

# 7. Before Writing Code

코드를 작성하기 전에 반드시 아래 내용을 먼저 출력한다.

## Requirement

요구사항 분석

## Design

설계 방향

## Test Cases

작성할 테스트

## Class Structure

생성될 클래스

## Implementation Plan

구현 순서

설계를 완료한 이후에만 코드를 작성한다.

---

# 8. Scope Rule

Codex는 요구사항 이상의 기능을 절대로 구현하지 않는다.

예를 들어

회원가입 기능을 요청받았다면

구현하는 것은

- 회원가입

뿐이다.

아래 기능은 요청받기 전까지 절대로 구현하지 않는다.

- JWT
- Refresh Token
- OAuth
- Email 인증
- SMS 인증
- Admin
- Role
- Monitoring

항상

YAGNI

원칙을 따른다.

---

# 9. Architecture

Layered Architecture를 따른다.

```

Controller

↓

Service

↓

Repository

↓

Database

```

Controller

- Request 수신
- Validation
- Response 반환

Service

- 비즈니스 로직

Repository

- 데이터 접근

Entity

- 상태와 비즈니스 규칙

Controller에는 비즈니스 로직을 작성하지 않는다.

Repository에는 비즈니스 로직을 작성하지 않는다.

---

# 10. Package Structure

```

com.piuda.wilson

global

config

common

error

infrastructure

grpc

audio

ai

prompt

domain

auth

caregiver

elderly

conversation

notification

analysis

dashboard

member

```

도메인별 응집도를 유지한다.

---

# 11. Entity Rules

Entity는

비즈니스 규칙을 가진다.

규칙

- Setter 금지
- protected 생성자
- Builder 사용
- 비즈니스 메서드 제공
- Entity가 자신의 상태를 변경한다.

기본 전략

- Lazy Loading
- Cascade 최소화
- Fetch Join 필요한 곳만 사용

Entity를 Controller에서 직접 수정하지 않는다.

---

# 12. DTO Rules

모든 API는 DTO를 사용한다.

Entity를 직접 반환하지 않는다.

Request

↓

Service

↓

Response

구조를 유지한다.

Request DTO

Response DTO

분리한다.

---

# 13. Repository Rules

Repository에는

비즈니스 로직을 작성하지 않는다.

복잡한 조회는

CustomRepository

또는

QueryDSL

을 사용한다.

항상

N+1 문제를 고려한다.

---

# 14. Service Rules

Service는

하나의 유스케이스만 담당한다.

규칙

- 함수 길이 30줄 이하 권장
- if depth 2 이하 권장
- private method 적극 추출
- 중복 제거

Service끼리 직접 호출을 최소화한다.

---

# 15. Transaction Rules

조회

@Transactional(readOnly = true)

수정

@Transactional

Controller에는 Transaction을 작성하지 않는다.

---

# 16. Validation

Controller에서

@Valid

사용

Validation 실패는

GlobalExceptionHandler

에서 처리한다.

---

# 17. Exception

RuntimeException 직접 사용 금지

CustomException 사용

GlobalExceptionHandler 사용

ErrorCode Enum 관리

Fail Fast

원칙을 따른다.

---

# 18. Logging

System.out.println 금지

Slf4j 사용

민감정보 로그 출력 금지

로그 레벨

DEBUG

INFO

WARN

ERROR

목적에 맞게 사용한다.

---

# 19. AI Architecture Rules

AI 서버는 외부 시스템이다.

절대로 신뢰하지 않는다.

모든 AI 호출은

- timeout
- retry
- logging
- exception handling

을 고려한다.

gRPC Exception을 그대로 노출하지 않는다.

---

# 20. Prompt Rules

프롬프트는

Service 안에 직접 작성하지 않는다.

절대로 아래처럼 작성하지 않는다.

```java
String prompt = """
...
""";
```

프롬프트는

PromptBuilder

또는

PromptTemplate

에서 생성한다.

Service는

AI에게 무엇을 요청할지만 결정한다.

LLM 호출은 Adapter를 통해 수행한다.

---

# 21. Redis Rules

Redis는

- Cache
- Session
- Scheduler
- Conversation Context

에만 사용한다.

영구 데이터 저장 금지.

---

# 22. Security

비밀번호

BCrypt

JWT는 요청받은 이후 구현

민감정보 암호화

개인정보 로그 출력 금지

---

# 23. Test Rules

Service

Mockito 사용

Repository Mock

외부 AI Mock

Redis Mock

gRPC Mock

비즈니스 로직만 검증

Controller

MockMvc

Repository

@DataJpaTest

Utility

반드시 테스트

Entity 테스트는 작성하지 않는다.

모든 테스트는

Given

When

Then

패턴을 따른다.

---

# 24. Naming Rules

boolean

isDeleted

isVerified

hasPermission

Entity

Member

Conversation

Caregiver

Service

MemberService

Repository

MemberRepository

DTO

CreateMemberRequest

CreateMemberResponse

---

# 25. Git Rules

Commit은 기능 단위

Commit Message

feat:

fix:

refactor:

test:

docs:

style:

chore:

---

# 26. Codex Collaboration Rules

새로운 파일을 만들기 전에

기존 코드를 먼저 탐색한다.

이미 존재하는

- DTO
- Exception
- Util
- Config
- Response

를 재사용한다.

중복 클래스를 만들지 않는다.

기존 코드 스타일을 우선 따른다.

새 라이브러리를 추가할 경우

왜 필요한지 먼저 설명한다.

---

# 27. Code Generation Rules

Codex는

절대로

- 추측해서 구현하지 않는다.
- 요구사항 이상 구현하지 않는다.
- TODO만 남기고 끝내지 않는다.

요구사항이 모호하면

반드시 질문한다.

---

# 28. Definition of Done

기능이 완료되기 위한 조건

- 모든 테스트 통과
- 테스트 코드 작성 완료
- Validation 완료
- Exception 처리 완료
- Logging 완료
- API 응답 통일
- 코드 중복 제거
- 리팩토링 완료

테스트가 없다면

그 기능은 완료된 것이 아니다.