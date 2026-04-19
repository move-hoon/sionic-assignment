# REVIEW

## 1. 과제를 어떻게 분석했는가
이 과제는 구현량 자체보다, 제한된 3시간 안에 무엇을 먼저 끝내고 어떤 리스크를 남길지 판단하는 문제가 더 중요하다고 해석했습니다.  
그래서 처음부터 기능을 넓게 쌓기보다, 아래 기준으로 우선순위를 정했습니다.

1. 시연 목표와 직접 연결되는가  
2. business rule이 있는가  
3. 설명 가치가 높은가  
4. 테스트로 검증 가능한가

이 기준으로 보면 가장 중요한 영역은 chat/thread였습니다. 단순 CRUD가 아니라:
- 마지막 질문 시점 기준 30분 규칙
- 이전 대화 문맥 유지
- thread 단위 pagination
- member/admin 권한 차등
- `model`, `isStreaming` 옵션 수용

을 동시에 보여줘야 했기 때문입니다.

그래서 실제 우선순위는:
- 인증
- chat/thread
- feedback
- admin activity/report
- 문서와 테스트

순으로 두었습니다.

## 2. AI를 어떻게 활용했는가 / 어떤 어려움이 있었는가
AI는 아래 영역에서 적극적으로 활용했습니다.
- 요구사항을 세부 체크리스트로 분해
- edge case 식별
  - 30분 경계값
  - thread-first pagination
  - streaming 저장 시점
  - feedback ownership / duplicate rule
- 테스트 시나리오 초안 작성
- OpenAI Chat Completions와 현재 코드 구조 차이 분석

하지만 AI 제안을 그대로 채택하지는 않았습니다. 아래는 직접 판단하고 검증했습니다.
- pagination 단위는 chat이 아니라 **thread** 여야 한다는 점
- 삭제 순서는 cascade에 맡기지 않고 `feedback -> chat -> thread`로 서비스에서 직접 처리하는 점
- 요청 DTO의 `model`이 실제 provider 호출까지 반영되어야 한다는 점
- streaming은 “응답 형식”뿐 아니라 “DB 저장 완료 시점”까지 설계해야 한다는 점
- 기존 Gemini one-shot prompt 구조를 OpenAI message list 구조로 바꾸기 위해 AI 경계를 넓혀야 한다는 점

가장 큰 어려움은 요구사항 원문은 OpenAI 예시를 기준으로 설명하지만, 기존 구현은 Gemini 중심의 단일 prompt 기반이었다는 점입니다.  
그래서 단순 provider 교체가 아니라:
- `prompt` → `messages`
- sync-only → `sync + stream`
- request DTO 계약 → 실제 provider 호출 계약

으로 구조를 넓히는 작업이 필요했습니다.

## 3. 가장 어려웠던 기능
가장 어려웠던 기능은 **stateful chat + OpenAI message history + streaming persistence**를 동시에 만족시키는 부분이었습니다.

이 기능이 어려운 이유는 세 가지였습니다.
1. thread는 마지막 질문 시점 기준으로 재사용/분기되어야 함
2. OpenAI 요청은 이전 대화를 `developer/user/assistant/user` 순의 messages로 정확히 보내야 함
3. streaming일 때는 delta를 먼저 전달하면서도, 최종 완료 전에는 partial chat을 저장하지 않아야 함

기존 구현은 이전 대화를 문자열 prompt 하나로 합치는 방식이었습니다. 이 방식은 간단하지만 OpenAI Chat Completions 예시와 정확히 맞지 않았습니다.  
그래서 서비스 계층에서 대화 문맥을 structured messages로 조립하도록 변경했습니다.

최종 조립 방식:
- `developer` instruction
- 이전 `user` 질문
- 이전 `assistant` 답변
- 현재 `user` 질문

그리고 streaming 경로에서는 SSE로 delta를 보내되, provider stream이 정상 완료된 뒤에만 chat을 저장하도록 정리했습니다.

## 4. 구현 범위와 우선순위 판단
배점 안내상 구현량은 낮은 비중이라고 되어 있었지만, 최종적으로는 요구사항을 전부 구현하는 방향으로 마무리했습니다. 다만 “널리”보다 “끝까지”를 우선했고, 모든 핵심 기능은 테스트로 고정하려고 했습니다.

### 최종 구현 범위
- 회원가입 / 로그인 / JWT 인증
- chat 생성 / thread 재사용 / thread 단위 목록 조회 / thread 삭제
- OpenAI Chat Completions 기반 `model` override
- SSE 기반 streaming 응답
- feedback 생성 / 목록 / 상태 변경
- admin activity 집계 / CSV report
- 통합 테스트 + OpenAI client 단위 테스트

## 5. 설계 요약
- 시간 타입: `Instant` + UTC
- ID: `UUID` 직접 생성
- 연관관계: JPA 객체 참조 대신 raw FK id 유지
- 삭제 정책: hard delete
- thread 삭제 순서: `feedback -> chat -> thread`
- `GET /api/v1/chats`: thread-first pagination
- `GET /api/v1/feedbacks`: feedback-first pagination
- OpenAI 호출: `/v1/chat/completions`
- 기본 모델: `gpt-5.4`
- streaming 응답: `text/event-stream`

AI 호출 경계는 `AiClient` / `AiRequest` / `AiResponse`로 유지하되, `AiRequest`를 OpenAI식 messages와 request option을 담을 수 있게 넓혔습니다.  
덕분에 controller/service는 provider 포맷보다 business rule에 집중할 수 있게 됐습니다.

## 6. 검증
최신 기준으로 아래 검증을 통과했습니다.
- OpenAI client unit test
  - non-stream request/response mapping
  - stream chunk aggregation
  - upstream error mapping
- chat integration test
  - model propagation
  - message history ordering
  - 30분 경계값
  - thread-first pagination
  - streaming persistence
  - thread delete ownership
- feedback integration test
- admin activity integration test
- 전체 테스트
- 전체 build

즉, 이 결과물은 “동작할 것 같다”가 아니라, 주요 요구사항이 테스트로 고정된 상태입니다.

## 7. 남은 리스크
요구사항 구현은 완료했지만, 운영 관점에서 남은 리스크는 있습니다.

1. **first-message concurrency**
   - 같은 사용자의 첫 질문이 거의 동시에 들어오면 thread가 둘 이상 생성될 수 있습니다.
   - 기존 thread 재사용 경쟁은 `@Lock(PESSIMISTIC_WRITE)`로 완화했지만, 첫 thread 생성 자체는 완전 직렬화하지 않았습니다.

2. **AI 실패 시 빈 thread 가능성**
   - 현재는 thread를 먼저 결정/저장한 뒤 OpenAI를 호출합니다.
   - 따라서 외부 AI 호출 실패 시 chat 없이 thread만 남을 수 있습니다.

3. **SSE 운영성 검증**
   - 기능 구현과 테스트는 완료했지만, 대량 동시 연결 / backpressure / 중간 disconnect 대응은 추가 운영 검토가 필요합니다.

4. **model allowlist 미적용**
   - 요청별 모델명은 실제 호출에 반영되지만, 허용 모델 목록 검증은 두지 않았습니다.
   - 잘못된 모델명은 외부 API 오류로 처리됩니다.

## 8. 마무리
이번 과제에서 보여드리고 싶었던 것은 단순히 “많이 구현했다”가 아니라,
- 모호한 요구사항을 구조화하고
- 중요한 business rule을 놓치지 않고
- 설명 가능한 형태로 마무리하고
- 테스트로 증명하는 개발 방식이었습니다.

핵심 포인트는 세 가지입니다.
- OpenAI 중심 요구사항에 맞춰 provider 경계를 바로잡았다.
- stateful chat의 핵심 복잡도(30분 규칙, thread-first pagination, message history)를 정확히 구현했다.
- streaming/model/history 같은 빠지기 쉬운 계약을 실제 코드와 테스트로 맞췄다.
