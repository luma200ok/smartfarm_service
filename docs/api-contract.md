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
- **차단(전부 403 A007, 서버측 강제 — FE 숨김은 보조)**: 회원 탈퇴(DELETE /users/me) · 농장 생성(POST /farms) · 농장 수정/삭제(PATCH/DELETE /farms/{id}) · 웹훅 설정(PATCH /farms/{id}/webhook) · 초대코드 발급(POST /farms/{id}/invitations) · 초대코드 수락(POST /invitations/accept) · 멤버 제거/농장 나가기(DELETE 멤버 계열) · **임계치 설정(PUT /farms/{id}/env-thresholds — 2026-08-22 #52 리뷰에서 추가: 데모 방문자의 공유 농장 영속 설정 변조 차단, OWNER 전용 write 경로 일관성)**.
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

## 4.7 AI 챗봇 (2026-08-23 확정, 이슈 #54·#55 — 다함 벤치마킹 3. ai-server 무변경 원칙 3번째 해제: smartfarm_ai 신규 챗 라우트)

**ai-server 신규 `POST /api/chat`** (smartfarm_ai 레포, 루프백 전용 — 조사 결과 기존 챗 엔드포인트 없음):
- 요청(Form, 기존 prescriptions 컨벤션 동일): `question: str(1~500, 필수)`, `caller_ref: str|None(≤64)`
- 처리: 기존 `retrieve(question, disease=None, k=3)` → 농업 도우미 프롬프트(병해·재배 환경 중심, 스코프 밖 질문은 짧은 안내) → qwen2.5:7b. **동시성은 기존 `inference_slot()` 세마포어 공유**(진단+처방+챗 합계 2, 포화 시 429 `{"detail"}`).
- 응답(신규 영문 스키마 — 기존 Prescription 한글 스키마 재사용 금지): `{answer: str, sources: [str], fallback: bool}` — LLM 실패 시 200+안내문+fallback=true(처방과 동일 트레이드오프).
- **이력(#66 정책 = 태깅 채택, 2026-08-23)**: `chat_messages` 테이블(id, created_at, question, answer, sources JSONB, caller_ref) — 처방 이력과 동일한 best-effort 저장(DB 미설정 시 no-op, 저장 실패가 응답을 막지 않음).

**backend (#54)**:
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/farms/{farmId}/chat` | 멤버 | ChatRequest{question(1~500)} | 200 ChatMessageResponse (동기, 타임아웃 30s) |
| GET | `/api/farms/{farmId}/chat` | 멤버 | `?page&size` | 200 Page\<ChatMessageResponse\> (최신순) |

- **ChatMessageResponse** `{id, question, answer, sources[], fallback, createdBy, createdAt}` — backend `chat_messages`(V12, farm_id·user_id 포함)에 이력 저장 후 매핑.
- caller_ref = `svc:farm:{farmId}` 전달. ai-server 429 → **CH002(429)**, 그 외 실패/타임아웃 → **CH001(502)**. 데모 계정 허용(체험 핵심 — 남용 쿼터는 #51에서 일괄).
- **자원 보호(2026-08-23 보안 리뷰 P2)**: ai-server는 챗 전용 하위 상한 1(진단·처방이 굶지 않도록 최소 1슬롯 확보) — 챗 포화 시 CH002. backend는 **3중 방어**를 둔다(2026-08-23 #54 보안 리뷰 P1 반영 — 원래 농장 단위만 두었으나, 일반 계정이 농장을 여러 개 만들어 곱하기로 우회하면 챗이 Tomcat 스레드를 30s씩 점유해 **사이트 전체 마비**가 가능함을 확인):
  1. **농장 단위** 분당 10건 (공유 농장의 합산 남용 차단)
  2. **사용자 단위** 분당 10건 (농장을 늘려 우회하는 경로 차단) — 둘 중 하나라도 초과하면 CH002
  3. **전역 동시성 가드** `Semaphore(2)` non-blocking — ai-server 챗 슬롯(1)에 대응. 확보 실패 시 **대기 없이 즉시 CH002**(대기하면 스레드를 점유해 방어 의미가 없다). 이 가드가 농장 수와 무관하게 스레드 점유 상한을 보장하는 핵심 방어다.
  - 인메모리 상태(단일 인스턴스 전제), 윈도 맵은 임계값 초과 시 만료 엔트리 정리. 농장 생성 개수 상한·`/api` 일반 레이트리밋은 별도 도메인이라 **이슈 #70**으로 분리.
- **저장형 XSS는 출력에서 막는다**: backend는 answer를 이스케이프하지 않는다(저장 시 이스케이프는 이중 이스케이프·본문 훼손을 부르는 안티패턴). 방어는 위 FE 렌더링 규칙이 담당한다.
- **⚠️ FE 렌더링 규칙(보안 필수)**: `answer`는 **LLM이 생성한 자유 텍스트**이므로 프롬프트 인젝션으로 스크립트 문자열이 섞일 수 있다. FE는 React 기본 이스케이프(텍스트 노드)로만 렌더링하고 **`dangerouslySetInnerHTML`·raw HTML 허용 마크다운 렌더러 사용 금지**(저장형 XSS 차단 — 답변이 이력으로 재노출되므로 1회성이 아님).

## 4.8 작업일지 · 날씨예보 (2026-08-23 확정, 이슈 #56·#57 — 다함 벤치마킹 4)

**작업일지** — `farm_logs`(V11): farm_id, author(user_id), log_date(DATE), type(enum), memo(≤1000), created_at.
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/farms/{farmId}/logs` | 멤버 | FarmLogRequest{logDate, type, memo?} | 201 FarmLogResponse |
| GET | `/api/farms/{farmId}/logs` | 멤버 | `?page&size` | 200 Page\<FarmLogResponse\> (logDate 내림차순) |
| PATCH | `/api/farms/{farmId}/logs/{logId}` | **작성자 본인만** | FarmLogRequest | 200 FarmLogResponse |
| DELETE | `/api/farms/{farmId}/logs/{logId}` | **작성자 본인 또는 OWNER** | — | 204 |

- type enum: `WATERING, FERTILIZING, PRUNING, HARVEST, PEST_CONTROL, ETC`(FE 라벨은 constants). 없음 **L001(404)**, 본인/OWNER 아님 **L002(403)**. 데모 계정 작성 허용(컨텐츠 생성 — 진단·처방과 동일 원칙), 수정·삭제는 본인 것만이라 자연 격리.
- **FarmLogResponse** `{id, logDate, type, memo, createdBy, createdAt}`

**날씨예보** — backend가 KMA 단기예보(공공데이터포털 getVilageFcst)를 직접 호출(ai-server 무관), **전역 고정 지점**(환경 대시보드와 동일 데모 온실 위치, env `KMA_GRID_NX/NY`).
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/environment/forecast` | 멤버 | — | 200 ForecastResponse (전역 60분 캐시+stale 폴백 — EnvironmentCache 패턴) |

- **ForecastResponse** `{updatedAt, points: [{time, temp?, humidity?, sky?, pop?}]}` — 향후 24h, 1시간 간격. sky=`SUNNY|CLOUDY|OVERCAST`, pop=강수확률(%). 캐시·stale 모두 없고 KMA 실패 → **W001(502)**.
- 신규 env: `KMA_SERVICE_KEY`(시크릿 — 배포 .env, 평문 커밋 금지), `KMA_GRID_NX`/`KMA_GRID_NY`. 타임아웃 5s.
- VPD·이슬점·몰리에르 지표는 FE 순수 계산(BE 없음) — 현재 온습도(environment/today) 입력.

## 4.9 양액 배합 (2026-08-23 확정, 이슈 #64·#65 — 다함 벤치마킹 5 '양액제어 대시보드'의 **계산·레시피 부분만**)

- **범위 한정(중요)**: 실제 양액기 제어(펌프·밸브·EC/pH 자동 조정)와 EC/pH 실시간 모니터링은 **범위 밖** — 장비 부재·센서에 EC/pH 없음(§4.6 스냅샷은 온습도뿐). 본 기능은 **배합 계산기 + 레시피 저장**이다.
- **안전 고지 의무**: 계산 결과 화면에 "참고용 — 적용 전 원수 분석·현장 확인 필요" 고지를 반드시 노출(FE). 프리셋 출처를 화면·코드 양쪽에 명시.
- **마이그레이션**: V13 `nutrient_recipes` (V11=#56 작업일지, V12=#54 챗 예약).

**프리셋**: DB가 아닌 **코드 리소스 상수**(버전 관리·출처 추적 목적). 값은 **반드시 공개 출처(농촌진흥청 표준 배양액·야마자키 처방 등)에서 인용**하고 파일 주석에 출처를 남긴다 — **임의 창작 금지**. 1차 작물=TOMATO(cropType 제약), 생육단계 enum `SEEDLING, VEGETATIVE, FRUITING, HARVEST`.

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/nutrient-presets` | 인증 | `?cropType=TOMATO` | 200 NutrientPresetResponse[] |
| POST | `/api/farms/{farmId}/nutrient-recipes/calculate` | 멤버 | NutrientRecipeRequest | 200 NutrientCalculationResponse (저장 없이 미리보기) |
| POST | `/api/farms/{farmId}/nutrient-recipes` | 멤버 | NutrientRecipeRequest{+name} | 201 NutrientRecipeResponse |
| GET | `/api/farms/{farmId}/nutrient-recipes` | 멤버 | `?page&size` | 200 Page\<NutrientRecipeSummaryResponse\> (최신순) |
| GET | `/api/farms/{farmId}/nutrient-recipes/{id}` | 멤버 | — | 200 NutrientRecipeResponse (계산 결과 동봉) |
| PATCH | `/api/farms/{farmId}/nutrient-recipes/{id}` | **작성자 본인** | NutrientRecipeRequest | 200 NutrientRecipeResponse |
| DELETE | `/api/farms/{farmId}/nutrient-recipes/{id}` | **작성자 본인 또는 OWNER** | — | 204 |

- **NutrientRecipeRequest** `{name?(1~50, 저장 시 필수), stage, target{n, p, k, ca, mg, s}(ppm, 각 0~1000), tankVolumeL(1~10000), concentrationFactor(1~500), sourceWater?{ca?, mg?, ec?}}` — sourceWater=원수 분석값(있으면 목표치에서 차감 보정).
- **NutrientCalculationResponse** `{tanks: [{tank: "A"|"B", items: [{fertilizer, formula, amountG}]}], estimatedEc, ionBalance{cationMeL, anionMeL, deviationPercent}, warnings: [str]}`
- **NutrientRecipeResponse** `{id, name, stage, target{...}, tankVolumeL, concentrationFactor, sourceWater?, calculation(NutrientCalculationResponse), createdBy, createdAt, updatedAt}` / **Summary**는 `{id, name, stage, estimatedEc, createdBy, createdAt}`.

**배합 규칙(서버 강제 — 위반 시 N003)**
- **A탱크 = 칼슘계**(질산칼슘 4수염, 킬레이트철) / **B탱크 = 인산·황산계**(제1인산칼륨, 황산마그네슘, **황산칼륨**, 미량요소 혼합제). 질산칼륨은 N 보충이 필요한 만큼 A탱크에 배치. 같은 탱크에 **Ca와 (PO4 또는 SO4) 동시 배치 금지** — 인산칼슘·황산칼슘 침전. 이 규칙은 코드 상수가 아니라 **검증 로직으로 강제**하고 테스트로 고정한다.
  - **2026-08-23 계약 정정(#64 리뷰 P1)**: 초판은 "B탱크에 질산칼륨 잔량"으로 K를 채우게 적었으나 **화학적으로 틀렸다** — Ca(NO3)2로 Ca를 맞춘 뒤 N이 이미 목표에 도달한 상태에서 K를 KNO3로 더 넣으면 **N이 목표를 초과**한다. N 초과는 도장·EC 상승으로 직접 피해가 가는 반면, K2SO4가 함께 들이는 S는 출처(HYG-1437)가 **10~200 mg/L 범위 허용**으로 명시한 성분이다(구현 실측 62~141 mg/L로 범위 내). 따라서 **N 초과를 피하고 S 여유 범위를 쓰는 K2SO4가 올바른 선택**이며, 구현이 맞고 계약 초판을 정정한다.
- 산출 투입량이 음수(원수 보정 과다)면 N003 + 어떤 성분이 초과인지 warnings에 명시.
- **이온 밸런스**: 양이온 me/L 합과 음이온 me/L 합의 편차를 **목표(target) ppm 기준**으로 계산한다(실제 투입량 기준은 중성 염 조합이라 전하보존으로 항상 0%에 수렴 — 경고가 절대 발동하지 못한다). **임계값 30%**, 초과 시 계산은 반환하되 warnings 경고.
  - **임계값 근거(2026-08-23 정정)**: 초판 10%는 근거 없는 값이었다. 프리셋 목표가 **N을 전량 NO3-N으로 가정**(출처가 NH4 분율을 주지 않음)하고 원수의 중탄산·Cl 등을 계산에 넣지 않으므로, **출처 검증된 정상 프리셋조차 17~25% 편차**가 나온다(SEEDLING 25.3·VEGETATIVE 22.1·FRUITING 19.3·HARVEST 17.6). 10%로 두면 모든 정상 처방에 경고가 상시 떠 **경고 자체가 무의미해진다**(alarm fatigue). 참조 데이터 위쪽인 30%로 잡아 "정상 범위를 벗어난 목표"만 걸리게 한다.
  - warnings 문구는 편차 수치와 함께 **NO3-N 전량 가정 때문에 편차가 과대 산출된다는 한계**를 밝혀, 사용자가 수치를 스스로 판단할 수 있게 한다.
- EC 추정: `EC(dS/m) ≈ 양이온 me/L 합 / 10` — **양이온만** 사용한다(양·음이온을 더하면 이중 계상으로 실측 대비 과대추정). 원예 통용 근사이므로 참고값임을 UI에 명시.
- **S(황) 목표**: 출처가 범위(10~200 mg/L)만 제시하므로 고정값이 없다. Mg 목표를 MgSO4 단일 공급원으로 충족할 때 따라오는 S량을 화학양론으로 역산해 목표로 삼는다(임의 추정 아님).
- 데모 계정: 계산·저장 허용(체험 핵심), 수정·삭제는 본인 것만이라 자연 격리(§4.5 차단 목록 미추가).

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
| CH001 | 502 | 챗 응답 실패(AI 서버 오류·타임아웃) |
| CH002 | 429 | AI 서버 혼잡(잠시 후 재시도) |
| L001 | 404 | 작업일지 없음 |
| L002 | 403 | 작업일지 수정/삭제 권한 없음(작성자 본인·삭제는 OWNER 겸용) |
| W001 | 502 | 날씨예보 조회 실패(KMA 오류·캐시 없음) |
| N001 | 404 | 양액 레시피 없음 |
| N002 | 403 | 양액 레시피 수정/삭제 권한 없음(작성자 본인·삭제는 OWNER 겸용) |
| N003 | 400 | 배합 불가(탱크 침전 위험·원수 보정 과다로 투입량 음수 등) |

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
