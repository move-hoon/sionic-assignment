---
name: assignment
description: Use this skill when implementing the Sionic AI chatbot take-home. It freezes the delivery strategy, domain rules, API behavior, access control, and assignment-specific assumptions so implementation stays aligned with the 3-hour constraint and scoring criteria.
---

# AI Chatbot Service - Assignment Delivery Spec

## Goal
- Deliver an API that proves "AI can be used through our service API" within a 3-hour take-home window.
- Optimize for correctness, prioritization, and explanation quality over raw feature count.
- Keep the design extensible enough for future provider swap / RAG expansion without overbuilding a platform.

## Tech Stack
- Kotlin 1.9.x
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL 15.8+ as the primary database
- JWT authentication
- BCrypt password hashing
- AI provider integration behind an abstraction boundary
- Tests and verification should run against PostgreSQL via Testcontainers

## Repository Alignment Rules
- Follow the current project response contract: successful responses use `ApiResponse`, failures use the existing `ErrorResponse` path.
- Do not introduce RFC 7807 `ProblemDetail` only for this assignment unless the whole repo is migrated consistently.
- Do not add soft delete solely for this take-home. Use hard delete unless repo-wide soft delete infrastructure is already implemented and verified.
- If JWT / Spring Security dependencies are missing, add them as part of the assignment setup rather than working around authentication requirements.

## Assignment Strategy
- This assignment rewards analysis, prioritization, AI-usage explanation, and judgment more than feature volume.
- Build the smallest complete vertical slice first.
- Freeze assumptions before coding so README can explain tradeoffs instead of apologizing for them afterward.
- Prefer one clean contract over flexible-but-unclear design.

## Scoring-Oriented Deliverables
The final submission must make these easy to evaluate:

1. How the requirements were analyzed
2. How AI was used during the work, and what difficulties existed
3. Which feature was hardest and why
4. What was intentionally prioritized or deferred under the 3-hour limit

README quality is part of the solution, not an afterthought.

## Cross-Cutting Decisions

### Timestamp Semantics
- Treat all persisted timestamps as UTC-based `timestamp with time zone` semantics.
- The thread reuse rule uses the question arrival time at the server, not the eventual chat persistence time.
- Use `Instant` as the default application type for persisted timestamps.
- Exact 30-minute boundary rule:
  - if `questionArrivedAt >= lastQuestionAt + 30 minutes`, create a new thread
  - otherwise reuse the existing thread

### AI Call Boundary
- Do not keep a database transaction open during an external AI call.
- Thread selection should be decided before the AI request.
- V1 correctness is based on the latest persisted state at request start.
- Simultaneous same-user first-message races may still create separate threads unless explicit serialization is added; if not addressed in code, document it as a known limitation.

### Delete Policy
- Default policy for this take-home: `hard delete`.
- Deleting a thread deletes its chats.
- Feedback that belongs to deleted chats must also be deleted or prevented by FK cascade rules.
- Deleted data is excluded from activity counts and CSV output because it no longer exists in V1 storage.

### Error Contract
- Validation errors, auth errors, permission errors, duplicate feedback, and missing resources must all follow the repository's existing exception/response style.
- Do not mix multiple error formats in the same submission.

### Extension Policy
- Keep `AiClient` as the vendor boundary.
- `model` is part of the request contract and should be honored when the current provider abstraction supports it cleanly.
- `isStreaming` is part of the request contract.
- If full streaming delivery is deferred under the 3-hour constraint, document that explicitly in the README and keep the non-streaming path correct.

---

## Domain 1: Users and Authentication

### User Schema
| Field | Type | Constraint |
|------|------|------|
| id | UUID | PK |
| email | string | required, unique |
| password | string | required, BCrypt hash only |
| name | string | required |
| role | string | business values are `member`, `admin` |
| createdAt | timestamptz | auto (UTC) |

### AuthEvent Schema
Use this for exact 24-hour stats.

| Field | Type | Constraint |
|------|------|------|
| id | UUID | PK |
| userId | UUID (FK -> User) | required |
| type | enum | `REGISTER`, `LOGIN` |
| createdAt | timestamptz | auto (UTC) |

### Auth Rules
- Signup requires `email`, `password`, `name`
- Login requires `email`, `password`
- Signup always creates a member user
- Every request except signup/login requires a valid JWT
- JWT payload must include at least `userId` and `role`

### Initial Admin Assumption
- Because the prompt does not define an initial admin bootstrap path, assume the first admin is created by bootstrap seed on application startup
- Example source: `application-secret.properties` or equivalent secret config
- Seeding must be idempotent

### Endpoints
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

### Behavioral Notes
- Successful signup records `AuthEvent(REGISTER)`
- Successful login records `AuthEvent(LOGIN)`
- Failed login attempts do not count toward login stats
- Plain-text password storage or logging is forbidden

---

## Domain 2: Chats and Threads

### Thread Schema
| Field | Type | Constraint |
|------|------|------|
| id | UUID | PK |
| userId | UUID (FK -> User) | required |
| lastQuestionAt | timestamptz | required (UTC) |
| createdAt | timestamptz | auto (UTC) |

### Chat Schema
| Field | Type | Constraint |
|------|------|------|
| id | UUID | PK |
| threadId | UUID (FK -> Thread) | required |
| question | string | required |
| answer | string | required |
| model | string | optional but recommended |
| createdAt | timestamptz | auto (UTC) |

### Thread Rule
When a user sends a question:

1. If the user has no prior thread, create a new thread
2. If the latest thread's `lastQuestionAt` is 30 minutes old or older, create a new thread
3. Otherwise reuse the latest thread

Business-time source:
- use the request arrival time captured by the server
- do not base the rule on `Chat.createdAt`

Recommended implementation shape:
1. Capture `questionArrivedAt`
2. Read the user's latest thread using persisted state
3. Decide create-or-reuse using `lastQuestionAt`
4. Call AI outside a long transaction
5. Persist the chat result and update/create thread state

### Chat Creation Endpoint
- `POST /api/v1/chats`

Request:
- `question`: required
- `isStreaming`: optional
- `model`: optional

Synchronous V1 behavior:
1. Determine the active thread
2. Load prior chats in that thread
3. Convert them to the provider request format
4. Request the AI answer
5. Persist the chat
6. Return the created chat response

History payload guidance:
- send prior exchanges in chronological order
- preserve alternating user / assistant context

### Streaming Policy
- `isStreaming` is part of the API contract
- If implemented, stream via SSE
- If full streaming delivery is not completed within the time budget, explicitly document that it was deferred
- Do not break the synchronous flow just to partially support streaming

### Model Override Policy
- If `model` is provided and the current AI client supports it cleanly, honor it
- Otherwise document that the server uses the configured default model
- Do not contort the abstraction just to satisfy this flag

### Chat List Endpoint
- `GET /api/v1/chats`

Contract:
- paginate by `thread`, not by `chat`
- each thread item includes its chats
- sorting applies to threads
- chats inside a thread should be returned in chronological order

Access control:
- `MEMBER`: only own threads and chats
- `ADMIN`: all threads and chats

Sorting / pagination:
- use thread-level pagination
- support `createdAt` ascending / descending ordering for threads
- support page / size style pagination parameters

### Thread Delete Endpoint
- `DELETE /api/v1/threads/{threadId}`

Access control:
- only the thread owner may delete
- because the prompt only guarantees owner deletion, assume admin does not gain cross-user delete power

Delete behavior:
- hard delete the thread
- delete related chats
- delete or cascade related feedback rows

---

## Domain 3: Feedback

### Feedback Schema
| Field | Type | Constraint |
|------|------|------|
| id | UUID | PK |
| userId | UUID (FK -> User) | required |
| chatId | UUID (FK -> Chat) | required |
| isPositive | boolean | required |
| status | enum | `PENDING`, `RESOLVED` |
| createdAt | timestamptz | auto (UTC) |

### Uniqueness Rule
- one user may create at most one feedback per chat
- enforce with a `(userId, chatId)` unique constraint
- duplicates should return `409 Conflict` using the repository's normal error format

### Feedback Create Endpoint
- `POST /api/v1/feedbacks`

Access control:
- `MEMBER`: only for chats they own
- `ADMIN`: for any chat

### Feedback List Endpoint
- `GET /api/v1/feedbacks`

Access control:
- `MEMBER`: only own feedback
- `ADMIN`: all feedback

Filters / pagination:
- support page / size style pagination parameters
- support `createdAt` ascending / descending ordering
- optional `isPositive` filter

### Feedback Status Endpoint
- `PATCH /api/v1/feedbacks/{feedbackId}/status`

Access control:
- admin only

Allowed state change:
- `PENDING` <-> `RESOLVED`

---

## Domain 4: Admin Analytics and Reporting

### Activity Endpoint
- `GET /api/v1/admin/activity`

Access control:
- admin only

Return exact counts for the last 24 hours:
- signup count from `AuthEvent(REGISTER)`
- login count from `AuthEvent(LOGIN)`
- chat count from persisted chats created in the same 24-hour window

Counting policy:
- use request-time "last 24 hours from now"
- failed logins are excluded
- deleted chats are excluded in V1 hard-delete semantics

### CSV Report Endpoint
- `GET /api/v1/admin/report`

Access control:
- admin only

Return a CSV for chats created in the last 24 hours.

Minimum columns:
- `userId`
- `userEmail`
- `userName`
- `threadId`
- `question`
- `answer`
- `createdAt`

CSV rules:
- UTF-8
- proper escaping for commas, quotes, and newlines
- `Content-Type: text/csv`
- `Content-Disposition: attachment; filename=report.csv`

---

## Access Matrix

| Capability | MEMBER | ADMIN |
|------|:------:|:------:|
| Register / login | ✅ | ✅ |
| Create chat | ✅ | ✅ |
| List chats | Own only | All |
| Delete thread | Own only | Own only |
| Create feedback | Own chats only | All chats |
| List feedback | Own only | All |
| Update feedback status | ❌ | ✅ |
| View activity stats | ❌ | ✅ |
| Download CSV report | ❌ | ✅ |

---

## Priority Plan

### P0 - Must Land
1. Signup / login / JWT authentication
2. Chat creation with thread create-or-reuse rule
3. Thread-grouped chat listing with RBAC
4. Exact activity counting with `AuthEvent`

### P1 - Strong Completion
5. Feedback create / list / status update
6. Thread deletion
7. Clean README with assumptions, tradeoffs, AI usage notes

### P2 - Nice to Have
8. Per-request model override
9. CSV report
10. Streaming response

If time is tight, preserve correctness in P0/P1 before touching P2.

---

## README Must Include
- Requirement analysis summary
- Prioritization rationale
- Assumptions section
- AI usage notes
- Hardest feature explanation
- Deferred scope and why it was deferred
- Run instructions
- API summary

## Frozen Assumptions
1. Signup never accepts a role; new users are always `MEMBER`
2. Internal enum/code representation may use `MEMBER` / `ADMIN`, but the business role values correspond to `member` / `admin`
3. Initial admin is bootstrapped by configuration seed because the prompt does not define another bootstrap path
4. The 30-minute rule uses question arrival time on the server
5. `>= 30 minutes` means start a new thread
6. Chat listing paginates threads, not chats
7. Thread deletion is owner-only, even for admin
8. V1 uses hard delete semantics
9. Feedback duplicates return `409 Conflict`
10. Exact login stats require `AuthEvent`; `last_login_at` is not a substitute
11. Full streaming delivery may be deferred only if documented honestly

## Known Limitations You May Document
- Concurrent same-user first-message races may create separate threads if strict serialization is not implemented
- Streaming may be omitted to protect core correctness
- Provider-specific model override may be limited by the current `AiClient` abstraction

## Architecture Intent
- Use service boundaries and `AiClient` abstraction to stay extensible
- Do not add abstractions that are not serving a real assignment requirement
- Favor a small, clean, explainable system over speculative platform design
