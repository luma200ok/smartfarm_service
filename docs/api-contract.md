# API Contract — smartfarm_service

> **단일 진실 소스.** 변경은 A(메타)만. B·C는 변경 필요 시 PR에 `[CONTRACT-CHANGE-REQUEST]` 표시 후 보류.
> 최초 작성: 2026-08-19 (Phase 0)

## 0. 아키텍처

```
브라우저 ── https://farm.luma200ok.com (Cloudflare → arm1 nginx)
              ├─ /            → Next.js standalone (127.0.0.1:3000, systemd)
              └─ /api         → Spring Boot (127.0.0.1:8085, systemd, -Xmx512m)
                                  ├─ PostgreSQL 16 (native, DB: smartfarm_service)
                                  └─ ai-server (127.0.0.1:8000, 기존 smartfarm_ai 레포
                                                FastAPI smartfarm-api.service — 외부 비노출)
```

- **레포 분리**: 이 레포 = `backend/` + `frontend/` + `docs/`. ai-server는 기존 `smartfarm_ai` 레포 유지(1차 범위에서 ai-server 코드 무변경).
- **외부 의존 없음**: LLM = arm1 로컬 Ollama(ai-server 경유), 전부 자가호스팅.

## 1. 인증 모델

- **JWT**: access 30분 + refresh 14일. refresh는 DB 저장·로테이션(재사용 감지 시 전체 무효화).
- 요청 헤더: `Authorization: Bearer {accessToken}`.
- 프론트 저장 키: `localStorage['farmAccessToken']` / `['farmRefreshToken']` (앱 prefix = `farm`).
- 인증 실패 401(A003/A004) / 권한 없음 403(A005/F002/F003).
- **A003 = access 토큰 만료(refresh로 회복 가능한 유일한 401)**. `/api/auth/refresh` 자체의 만료·무효·재사용은 전부 **A004**(재로그인 필요) — 클라이언트는 A003만 refresh 재시도한다. (2026-08-19 확정)
- **클라이언트 refresh는 single-flight 의무**: 동시 401 시 refresh 요청은 1개만 발화하고 나머지는 그 결과를 공유(로테이션 1회성 토큰이라 병렬 refresh는 재사용 감지 오탐 유발). FE `lib/api/auth.ts` in-flight Promise로 구현됨.
- 로그아웃·전체 무효화 후에도 이미 발급된 access 토큰은 stateless 특성상 최대 30분 유효(수용된 트레이드오프).

## 2. 멀티테넌시 (Farm = 테넌트)

- 테넌트 식별 = **path param `{farmId}`**. 입력값 취급 — **매 요청 멤버십 재검증**(cross-tenant IDOR 차단), repository 조회는 항상 farm 스코프.
- 역할 2단계로 시작: `OWNER`(농장 관리·초대·삭제) / `MEMBER`(조회·진단·처방). MANAGER는 후속.
- 합류 = 초대코드(OWNER 발급, 만료 72h, 만료까지 다인 재사용 가능). **폐기 정책(2026-08-19 확정)**: 농장당 활성 코드 1건 — 재발급 시 기존 코드 무효화, **멤버 제거·자발 탈퇴 시 해당 농장 활성 코드 자동 무효화**(제거·탈퇴한 멤버의 보유 코드 재합류 차단). 무효화된 코드는 F004. 코드는 DB에 SHA-256 해시로만 저장.

## 3. 핵심 엔드포인트

| Method | URL | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/auth/signup` | 공개 | SignupRequest | 201 UserResponse |
| POST | `/api/auth/login` | 공개 | LoginRequest | 200 TokenResponse |
| POST | `/api/auth/refresh` | 공개 | RefreshRequest | 200 TokenResponse |
| POST | `/api/auth/logout` | 인증 | RefreshRequest | 204 |
| GET | `/api/users/me` | 인증 | — | 200 UserResponse |
| POST | `/api/farms` | 인증 | FarmRequest | 201 FarmResponse (생성자=OWNER) |
| GET | `/api/farms` | 인증 | — | 200 List\<FarmSummaryResponse\> (내 농장) |
| GET | `/api/farms/{farmId}` | 멤버 | — | 200 FarmResponse |
| PATCH | `/api/farms/{farmId}` | OWNER | FarmUpdateRequest(null=미변경. location 비우기는 1차 미지원 — 후속) | 200 FarmResponse |
| DELETE | `/api/farms/{farmId}` | OWNER | — | 204 (soft delete) |
| POST | `/api/farms/{farmId}/invitations` | OWNER | — | 201 InvitationResponse |
| POST | `/api/invitations/accept` | 인증 | AcceptInvitationRequest | 200 FarmResponse |
| GET | `/api/farms/{farmId}/members` | 멤버 | — | 200 List\<MemberResponse\> |
| DELETE | `/api/farms/{farmId}/members/{memberId}` | OWNER 또는 본인 | — | 204 (OWNER 본인 탈퇴는 불가 → F006) |
| POST | `/api/farms/{farmId}/diagnoses` | 멤버 | multipart `file` | 201 DiagnosisResponse (동기) |
| GET | `/api/farms/{farmId}/diagnoses` | 멤버 | `?page&size` | 200 Page\<DiagnosisSummaryResponse\> |
| GET | `/api/farms/{farmId}/diagnoses/{diagnosisId}` | 멤버 | — | 200 DiagnosisResponse |
| POST | `/api/farms/{farmId}/prescriptions` | 멤버 | PrescriptionRequest | **202** PrescriptionResponse(PENDING) |
| GET | `/api/farms/{farmId}/prescriptions/{prescriptionId}` | 멤버 | — | 200 PrescriptionResponse (폴링용) |
| GET | `/api/farms/{farmId}/prescriptions` | 멤버 | `?page&size` | 200 Page\<PrescriptionSummaryResponse\> |

### 처방 비동기 job (Ollama 직렬화)
- POST 시 `PENDING` 저장 후 202 즉시 반환 → **backend 내 단일 스레드 executor**가 순차 처리(`PROCESSING`) → ai-server `POST /api/prescriptions`(동기, 웜 ~16s) 호출 → `COMPLETED`(result 저장) / `FAILED`(P002).
- ai-server 429(혼잡) 시 백오프 재시도 2회 후 FAILED. 프론트는 2~3초 간격 폴링.
- status: `PENDING → PROCESSING → COMPLETED | FAILED`.
- **접수 상한(2026-08-19 확정)**: 농장당 진행 중(PENDING+PROCESSING) 3건 초과 접수 → **P004(429)**. 워커 큐는 유한(포화 시 저장 없이 P004). 재기동 복구 재큐잉도 상한·연령 컷오프 적용(초과분 P002 FAILED).

### ai-server 연동 (기존 계약 그대로, 변경 없음)
| 용도 | 호출 | 비고 |
|---|---|---|
| 진단 | `POST http://127.0.0.1:8000/api/diagnoses` (multipart file) | 동기 ~1-2s. `ood_blocked` 응답도 200 |
| 처방 | `POST http://127.0.0.1:8000/api/prescriptions` (multipart: question, diagnosis?=JSON문자열) | 동기 ~16s, 동시성 캡 2·혼잡 429. 진단 재사용 시 저장된 진단 JSON을 `diagnosis` 필드로 전달. **응답 스키마는 한글 키**(`진단요약·원인·즉시조치·예방·재촬영시점·근거출처` — src/llm/prescribe.py Prescription) — **backend가 contract §4 영문 result 스키마로 매핑**(진단요약→summary, 즉시조치+예방→actions, 원인/재촬영시점→caution 계열, 근거출처→sources). 필수 필드(summary) null/blank면 P002 FAILED(빈 성공 금지). 필드별 크기 캡 적용. ai-server가 LLM 실패 시에도 200+실패 안내문을 줄 수 있음 — 이는 COMPLETED로 수용(수용된 트레이드오프, 2026-08-19) |

## 4. DTO 스키마 (주요)

- **SignupRequest** `{email, password(8+), nickname(2~20)}` / **LoginRequest** `{email, password}` / **RefreshRequest** `{refreshToken}`
- **TokenResponse** `{accessToken, refreshToken}` / **UserResponse** `{id, email, nickname, createdAt}`
- **FarmRequest** `{name(2~50), cropType, location?}` — cropType enum: `TOMATO`(1차, ai-server 모델이 토마토 전용) 확장 대비 enum
- **FarmResponse** `{id, name, cropType, location, myRole, memberCount, createdAt}` / **FarmSummaryResponse** `{id, name, cropType, myRole}`
- **InvitationResponse** `{code, expiresAt}` / **AcceptInvitationRequest** `{code}` — 시각 필드는 전부 서버 로컬(Asia/Seoul) naive datetime(레포 공통)
- **MemberResponse** `{memberId, userId, nickname, role, joinedAt}`
- **DiagnosisResponse** `{id, status(ok|ood_blocked), label, labelKr, prob, part, reason?, imageUrl?, camPngBase64?, createdBy, createdAt}` — ai-server DiagnosisResponse를 이력 엔티티로 저장 후 매핑
- **PrescriptionRequest** `{question(1~500), diagnosisId?}` 
- **PrescriptionResponse** `{id, status, question, diagnosisId?, result?{summary, actions[], caution, sources[]}, errorCode?, createdBy, createdAt, completedAt?}` — result는 ai-server `Prescription` 구조화 JSON 저장
- **Summary 필드 확정**: `DiagnosisSummaryResponse` `{id, status, label, labelKr, prob, part, createdBy, createdAt}` / `PrescriptionSummaryResponse` `{id, status, question, createdBy, createdAt, completedAt?}` (무거운 필드 base64·result 본문 제외).
- **PageResponse\<T\>** `{content: T[], page, size, totalElements, totalPages}` — backend는 Spring `Page` 직접 노출 금지, 이 record로 매핑.
- 이메일은 서버에서 `trim().toLowerCase()` 정규화 후 저장·비교(대소문자 무관 단일 계정).

## 5. ErrorCode 체계

응답 형식: `{timestamp, code, message}` — GlobalExceptionHandler 일괄.

| 코드 | HTTP | 의미 |
|---|---|---|
| C001 | 400 | 요청 검증 실패(Bean Validation·본문 파싱 실패 포함) |
| C002 | 500 | 내부 서버 오류 |
| C003 | 404 | 존재하지 않는 경로 |
| C004 | 405 | 허용되지 않는 메서드 |
| A001 | 409 | 이메일 중복 |
| A002 | 401 | 이메일/비밀번호 불일치 |
| A003 | 401 | access 토큰 만료(refresh로 회복 가능) |
| A004 | 401 | 토큰 무효(변조·재사용·refresh 만료/미존재 포함 — 재로그인) |
| A005 | 403 | 접근 권한 없음 |
| F001 | 404 | 농장 없음 |
| F002 | 403 | 농장 멤버 아님 |
| F003 | 403 | OWNER 권한 필요 |
| F004 | 400 | 초대코드 무효/만료 |
| F005 | 409 | 이미 농장 멤버 |
| F006 | 400 | OWNER는 탈퇴 불가(농장 삭제로만) |
| D001 | 404 | 진단 이력 없음 |
| D002 | 400 | 이미지 형식/크기 오류 |
| D003 | 502 | AI 서버 오류/불가 |
| P001 | 404 | 처방 이력 없음 |
| P002 | 500 | 처방 생성 실패(재시도 소진·응답 이상 포함) |
| P003 | 429 | AI 서버 혼잡(잠시 후 재시도) |
| P004 | 429 | 처방 대기 한도 초과(농장당 진행 중 상한 — 완료 후 재시도) |

## 6. 환경변수 · CORS

| 대상 | 키 | 값(운영) |
|---|---|---|
| backend | `SPRING_PROFILES_ACTIVE` | `prod` (**운영 필수** — Swagger 비활성 등 prod 설정이 이 프로필에 의존. systemd env 파일에 주입) |
| backend | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/smartfarm_service` |
| backend | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `/etc/app-secrets/smartfarm-service.env` (root:600) |
| backend | `JWT_SECRET` | 동일 env 파일 |
| backend | `AI_SERVER_URL` | `http://127.0.0.1:8000` |
| backend | `CORS_ALLOWED_ORIGINS` | `https://farm.luma200ok.com` (로컬: `http://localhost:3000`) |
| frontend | `NEXT_PUBLIC_API_URL` | `https://farm.luma200ok.com` (미설정 시 프로덕션 빌드 실패 처리) |

- Flyway: `V{n}__{설명}.sql`, 기존 마이그레이션 수정 금지, `spring.flyway.out-of-order=true` 초기 설정.
- 파일 저장: 진단 이미지는 1차에서 **저장하지 않음**(결과만 이력화, imageUrl은 후속) — 디스크·개인정보 부담 최소화.
