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

## 2. 멀티테넌시 (Farm = 테넌트)

- 테넌트 식별 = **path param `{farmId}`**. 입력값 취급 — **매 요청 멤버십 재검증**(cross-tenant IDOR 차단), repository 조회는 항상 farm 스코프.
- 역할 2단계로 시작: `OWNER`(농장 관리·초대·삭제) / `MEMBER`(조회·진단·처방). MANAGER는 후속.
- 합류 = 초대코드(OWNER 발급, 만료 72h, 1회용 아님·만료까지 재사용 가능).

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
| PATCH | `/api/farms/{farmId}` | OWNER | FarmRequest(부분) | 200 FarmResponse |
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

### ai-server 연동 (기존 계약 그대로, 변경 없음)
| 용도 | 호출 | 비고 |
|---|---|---|
| 진단 | `POST http://127.0.0.1:8000/api/diagnoses` (multipart file) | 동기 ~1-2s. `ood_blocked` 응답도 200 |
| 처방 | `POST http://127.0.0.1:8000/api/prescriptions` (multipart: question, diagnosis?=JSON문자열) | 동기 ~16s, 동시성 캡 2·혼잡 429. 진단 재사용 시 저장된 진단 JSON을 `diagnosis` 필드로 전달 |

## 4. DTO 스키마 (주요)

- **SignupRequest** `{email, password(8+), nickname(2~20)}` / **LoginRequest** `{email, password}` / **RefreshRequest** `{refreshToken}`
- **TokenResponse** `{accessToken, refreshToken}` / **UserResponse** `{id, email, nickname, createdAt}`
- **FarmRequest** `{name(2~50), cropType, location?}` — cropType enum: `TOMATO`(1차, ai-server 모델이 토마토 전용) 확장 대비 enum
- **FarmResponse** `{id, name, cropType, location, myRole, memberCount, createdAt}` / **FarmSummaryResponse** `{id, name, cropType, myRole}`
- **InvitationResponse** `{code, expiresAt}` / **AcceptInvitationRequest** `{code}`
- **MemberResponse** `{memberId, userId, nickname, role, joinedAt}`
- **DiagnosisResponse** `{id, status(ok|ood_blocked), label, labelKr, prob, part, reason?, imageUrl?, camPngBase64?, createdBy, createdAt}` — ai-server DiagnosisResponse를 이력 엔티티로 저장 후 매핑
- **PrescriptionRequest** `{question(1~500), diagnosisId?}` 
- **PrescriptionResponse** `{id, status, question, diagnosisId?, result?{summary, actions[], caution, sources[]}, errorCode?, createdBy, createdAt, completedAt?}` — result는 ai-server `Prescription` 구조화 JSON 저장
- 목록 Summary는 각 상세에서 무거운 필드(base64·result 본문) 제외.

## 5. ErrorCode 체계

응답 형식: `{timestamp, code, message}` — GlobalExceptionHandler 일괄.

| 코드 | HTTP | 의미 |
|---|---|---|
| C001 | 400 | 요청 검증 실패(Bean Validation) |
| C002 | 500 | 내부 서버 오류 |
| A001 | 409 | 이메일 중복 |
| A002 | 401 | 이메일/비밀번호 불일치 |
| A003 | 401 | 토큰 만료 |
| A004 | 401 | 토큰 무효(변조·재사용 감지 포함) |
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
| P002 | 500 | 처방 생성 실패(재시도 소진) |
| P003 | 429 | AI 서버 혼잡(잠시 후 재시도) |

## 6. 환경변수 · CORS

| 대상 | 키 | 값(운영) |
|---|---|---|
| backend | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/smartfarm_service` |
| backend | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `/etc/app-secrets/smartfarm-service.env` (root:600) |
| backend | `JWT_SECRET` | 동일 env 파일 |
| backend | `AI_SERVER_URL` | `http://127.0.0.1:8000` |
| backend | `CORS_ALLOWED_ORIGINS` | `https://farm.luma200ok.com` (로컬: `http://localhost:3000`) |
| frontend | `NEXT_PUBLIC_API_URL` | `https://farm.luma200ok.com` (미설정 시 프로덕션 빌드 실패 처리) |

- Flyway: `V{n}__{설명}.sql`, 기존 마이그레이션 수정 금지, `spring.flyway.out-of-order=true` 초기 설정.
- 파일 저장: 진단 이미지는 1차에서 **저장하지 않음**(결과만 이력화, imageUrl은 후속) — 디스크·개인정보 부담 최소화.
