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
| POST | `/api/auth/demo-login` | 공개 | — | 200 TokenResponse (데모 계정 토큰 발급 — 자격증명 불필요, §5-데모 참조) |
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
| DELETE | `/api/users/me` | 인증 | WithdrawRequest{password} — **비밀번호 재확인 필수**(불일치 A002. 토큰 탈취 단독으로 비가역 삭제 불가) | 204 (soft delete — **OWNER 농장 보유 시 409 A006**. 전 refresh 무효화+전 농장 멤버십 제거+해당 농장 활성 초대 무효화+**즉시 익명화**: email→`withdrawn-{id}@invalid`·nickname→`탈퇴회원`) |
| PATCH | `/api/farms/{farmId}/webhook` | OWNER | WebhookRequest{webhookUrl?: string\|null — null=해제, https·discord.com/api/webhooks 프리픽스 검증} | 200 FarmResponse |
| GET | `/api/farms/{farmId}/diagnoses/{diagnosisId}/image` | 멤버 | — | 200 image/* 스트리밍 (원본 미보유 구 데이터 404 D004) |
| GET | `/api/farms/{farmId}/environment/today` | 멤버 | — | 200 EnvironmentTodayResponse (ai-server 프록시, 60s 캐시 허용) |

### 2026-08-20 Phase 3 확장 (FR-7·탈퇴·알림·이미지 — ai-server 무변경 원칙 해제 결정)
- **회원 탈퇴**: soft delete + `revokeAllByUserId` + 본인 farm_members 전부 삭제(각 농장 활성 초대 무효화 동반 — 기존 탈퇴 정책 재사용). OWNER인 농장(살아있는 농장 기준)이 하나라도 있으면 **A006(409)** — 농장 삭제 후 탈퇴. 탈퇴 후 이메일 재가입 허용(partial unique index가 이미 보장).
- **탈퇴 봉쇄(2026-08-20 보안·코드 리뷰 확정)**: ① **FarmAccessGuard 멤버십 조회가 User 생존을 함께 검증**(JOIN User + @SQLRestriction — 전 farm-scoped 표면) + 가드 밖 진입점(농장 생성·초대 수락) 유저 생존 검사(A004) ② 탈퇴 트랜잭션은 users 행 잠금(FOR UPDATE)으로 동시 멤버십 생성·동시 탈퇴 직렬화 ③ 재인증: 비밀번호 재확인 ④ PII 즉시 익명화(email·nickname·비밀번호 해시 소거).
- **탈퇴 유저 잔존 데이터**: diagnoses/prescriptions의 createdBy는 탈퇴 후에도 원 userId를 유지(팀 이력 보존 — join 없어 PII 미노출, FE는 미해석 id 표기 허용). 수용된 정책.
- **알림(디스코드 웹훅)**: farms에 `webhook_url` 컬럼(nullable). 처방 **COMPLETED/FAILED 전이 시** 워커가 **트랜잭션 밖에서** 발송(실패는 로그만 — 알림 실패가 처방 상태에 영향 금지, 타임아웃 5s). URL은 응답에 마스킹(설정 여부 boolean `webhookConfigured`만 노출 — 멤버에게 URL 원문 비노출).
- **진단 이미지 저장**: 원본을 `${IMAGE_STORAGE_DIR}/{farmId}/{diagnosisId}.{ext}`에 저장(운영 /home/opc/apps/smartfarm-service/uploads). diagnoses에 `image_path` 컬럼. 응답 `imageUrl` = 위 GET 경로(테넌트 인가 필수라 nginx 정적 서빙 금지, backend 스트리밍). 저장 실패는 진단 자체를 실패시키지 않음(imageUrl null, WARN). 보존 정책은 후속.
- **환경 대시보드**: ai-server 신규 `GET /api/environment/today`(무변경 원칙 해제, smartfarm_ai#66) 프록시. **1차: 전 농장 공용 데모 온실 데이터**(farm↔센서 매핑은 후속). **ai-server 응답 확정(2026-08-20, snake_case — 진단과 동일하게 backend가 @JsonNaming 매핑)**: `{demo: true, updated_at, outdoor: {temp, humidity}, indoor: {temp, humidity, controlled}, devices: [{name, on}], alerts: [str]}` — 상태 파일·KMA 불가 시에도 **항상 200 + 가용 필드 + alerts 사유**. 서비스 응답 `EnvironmentTodayResponse`는 camelCase(`updatedAt`)로 변환, 60s 캐시.

### 처방 비동기 job (Ollama 직렬화)
- POST 시 `PENDING` 저장 후 202 즉시 반환 → **backend 내 단일 스레드 executor**가 순차 처리(`PROCESSING`) → ai-server `POST /api/prescriptions`(동기, 웜 ~16s) 호출 → `COMPLETED`(result 저장) / `FAILED`(P002).
- ai-server 429(혼잡) 시 백오프 재시도 2회 후 FAILED. 프론트는 2~3초 간격 폴링.
- status: `PENDING → PROCESSING → COMPLETED | FAILED`. **FAILED 시 `errorCode` = P002(생성 실패) 또는 P003(혼잡 재시도 소진)** — FE는 P003이면 재시도 유도 렌더.
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
- **FarmResponse** `{id, name, cropType, location, myRole, memberCount, webhookConfigured, createdAt}` — webhookConfigured=웹훅 설정 여부(URL 원문은 어떤 응답에도 미노출) / **FarmSummaryResponse** `{id, name, cropType, myRole}`
- **InvitationResponse** `{code, expiresAt}` / **AcceptInvitationRequest** `{code}` — 시각 필드는 전부 서버 로컬(Asia/Seoul) naive datetime(레포 공통)
- **MemberResponse** `{memberId, userId, nickname, role, joinedAt}`
- **DiagnosisResponse** `{id, status(ok|ood_blocked), label, labelKr, prob, part, reason?, imageUrl?, camPngBase64?, createdBy, createdAt}` — ai-server DiagnosisResponse를 이력 엔티티로 저장 후 매핑
- **PrescriptionRequest** `{question(1~500), diagnosisId?}` 
- **PrescriptionResponse** `{id, status, question, diagnosisId?, result?{summary, actions[], caution, sources[]}, errorCode?, createdBy, createdAt, completedAt?}` — result는 ai-server `Prescription` 구조화 JSON 저장
- **Summary 필드 확정**: `DiagnosisSummaryResponse` `{id, status, label, labelKr, prob, part, createdBy, createdAt}` / `PrescriptionSummaryResponse` `{id, status, question, createdBy, createdAt, completedAt?}` (무거운 필드 base64·result 본문 제외).
- **PageResponse\<T\>** `{content: T[], page, size, totalElements, totalPages}` — backend는 Spring `Page` 직접 노출 금지, 이 record로 매핑.
- 이메일은 서버에서 `trim().toLowerCase()` 정규화 후 저장·비교(대소문자 무관 단일 계정).

## 4.5 데모 계정 (2026-08-22 확정, 이슈 #49)

- **목적**: 포트폴리오 방문자가 회원가입 없이 체험. 로그인 화면 "데모 계정으로 체험하기" 버튼 → `POST /api/auth/demo-login`.
- **시드**: `users.is_demo`(boolean, Flyway 신규 마이그레이션) + 앱 기동 시 idempotent 시드(init/): 데모 유저(email `demo@smartfarm.local`, nickname `데모 계정`, 랜덤 비밀번호 해시 — 비밀번호 로그인 경로 미사용) + 데모 농장 1개(OWNER). 자격증명은 레포·문서 어디에도 평문 노출하지 않는다.
- **demo-login**: 데모 유저 조회 후 기존 토큰 발급 로직 재사용(TokenResponse). 데모 유저 존재는 시드가 보장하는 전제 — 미존재는 서버 결함이므로 C002(500)로 처리(A00x 오용 금지).
- **차단(전부 403 A007, 서버측 강제 — FE 숨김은 보조)**: 회원 탈퇴(DELETE /users/me) · 농장 생성(POST /farms) · 농장 수정/삭제(PATCH/DELETE /farms/{id}) · 웹훅 설정(PATCH /farms/{id}/webhook) · 초대코드 발급(POST /farms/{id}/invitations) · 초대코드 수락(POST /invitations/accept) · 멤버 제거/농장 나가기(DELETE 멤버 계열).
- **허용**: 전체 조회 + 진단 업로드 + 처방 생성(체험 핵심). 남용 대비 rate-limit은 후속 이슈.
- **FE**: 데모 로그인 후에는 일반 계정과 동일 UI(차단 작업은 서버 403 A007 메시지 표기). 차단 버튼 사전 숨김은 후속 폴리시.

## 4.6 환경 시계열·임계치 알림 (2026-08-22 확정, 이슈 #52·#53 — 다함 벤치마킹 1·2)

- **원칙**: **ai-server 무변경**. backend `@Scheduled` 폴러(60s fixedDelay)가 기존 ai-server `GET /api/environment/today`를 조회해 `env_snapshots`에 적재(+`EnvironmentCache` 갱신). 기존 요청 경로(on-demand 60s 캐시)는 폴백으로 유지.
- **적재 규칙**: ai-server 부분 응답 수용 — 가용 필드만 저장(전 필드 nullable). **직전 적재의 `updated_at`과 동일하면 skip**(ai-server 상태 파일 미갱신 시 중복 행 방지). 저장 필드: `captured_at(=updated_at)`, `outdoor_temp/humidity`, `indoor_temp/humidity`, `controlled`. devices·alerts는 시계열 미저장.
- **보존**: 90일. 일 1회 purge 스케줄러(RefreshTokenPurgeService 패턴).

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/environment/history` | 멤버 | `?range=24h\|7d\|30d` (기본 24h, 그 외 C001) | 200 EnvironmentHistoryResponse |
| GET | `/api/farms/{farmId}/env-thresholds` | 멤버 | — | 200 EnvThresholdsResponse (미설정 시 enabled=false 기본값) |
| PUT | `/api/farms/{farmId}/env-thresholds` | OWNER | EnvThresholdsRequest | 200 EnvThresholdsResponse |

- **EnvironmentHistoryResponse** `{range, points: [{capturedAt, outdoorTemp?, outdoorHumidity?, indoorTemp?, indoorHumidity?}]}` — 다운샘플: 24h=원본(60s), 7d=30분 평균, 30d=2시간 평균(DB 집계, 빈 구간은 점 생략).
- **EnvThresholdsRequest/Response** `{enabled, indoorTempMin?, indoorTempMax?, indoorHumidityMin?, indoorHumidityMax?}`(+Response에 `updatedAt`) — 검증: min<max, 온도 -50~80, 습도 0~100(위반 C001). 저장=`farm_env_thresholds`(farm당 1행).
- **임계치 알림**: 폴러가 적재 직후 `enabled=true`이고 `webhook_url` 설정된 농장 대상 **indoor 온·습도** 평가. **연속 2틱 이탈 시 발동**, 농장×항목×방향별 **쿨다운 30분**(단일 인스턴스 전제 — 메모리 상태 허용, 재시작 시 초기화 수용). 발송은 기존 디스코드 웹훅 노티파이어 컨벤션(타임아웃 5s·실패는 로그만·URL 마스킹) 준수. 신규 ErrorCode 없음(검증은 C001 재사용).
- **마이그레이션**: V9 `env_snapshots`, V10 `farm_env_thresholds` (V8은 #49 데모 선점 — **#49 먼저 머지**, out-of-order=true 확인됨).

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
| A006 | 409 | OWNER 농장 보유 — 탈퇴 불가(농장 삭제 후 재시도) |
| A007 | 403 | 데모 계정에서 허용되지 않는 작업 |
| F001 | 404 | 농장 없음 |
| F002 | 403 | 농장 멤버 아님 |
| F003 | 403 | OWNER 권한 필요 |
| F004 | 400 | 초대코드 무효/만료 |
| F005 | 409 | 이미 농장 멤버 |
| F006 | 400 | OWNER는 탈퇴 불가(농장 삭제로만) |
| D001 | 404 | 진단 이력 없음 |
| D002 | 400 | 이미지 형식/크기 오류 |
| D003 | 502 | AI 서버 오류/불가 |
| D004 | 404 | 진단 원본 이미지 없음(미저장 구 데이터 포함) |
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
