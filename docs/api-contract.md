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
- **역할 4단계**(2026-08-25 확정, #122/PR — 구 `OWNER`/`MEMBER` 2단계에서 확장):
  | 역할 | rank | 권한 |
  |---|---|---|
  | `ADMIN`(관리자) | 3 | 구조 CRUD(농장·존·랙·장비·임계값·**알람 규칙**·웹훅) · 초대 발급 · **멤버 관리·역할 변경** · 농장 삭제 + 아래 전부 |
  | `OPERATOR`(제어가능) | 2 | 제어(운전모드·목표값·적용·취소) · **비상 정지** · 알람 확인/처리 · 작업일지·양액 레시피 작성 · **챗·진단·처방** + 아래 전부 |
  | `VIEWER`(조회전용) | 1 | 조회만 |
  | `PENDING`(대기) | 0 | **farm-scoped 접근 전면 차단**(F008). 예외: **본인 멤버십 제거**(승인 대기 취소)만 허용 |
  - 판정은 `rank` 기준 `atLeast`(선언 순서가 아니라 명시적 rank — 순서가 바뀌어도 인가가 뒤집히지 않게). 가드 3단: `requireAdmin`(18) / `requireOperator`(19) / `requireMember`(29, PENDING 거부) + `requireAnyMembership`(1, 본인 탈퇴 전용).
  - **이관(V21)**: `OWNER→ADMIN`, `MEMBER→OPERATOR`. ⚠️ `MEMBER→VIEWER`는 **기능 회귀**다 — 구 MEMBER는 제어를 할 수 있었다.
  - ⚠️ **비상 정지가 OWNER 전용 → OPERATOR 이상으로 완화**됐다(사용자 결정). 구 MEMBER가 장비를 켜고 끄는 건 되면서 비상정지만 안 되는 불일치를 바로잡은 것.
  - ⚠️ **VIEWER 쓰기 UI 게이트**: 서버는 F007로 차단하지만, VIEWER에게 쓰기 버튼을 숨기는 FE 처리는 #123에서 다룬다.
- 합류 = 초대코드(**ADMIN** 발급, 만료 72h, 만료까지 다인 재사용 가능). ⚠️ **수락 = 즉시 활성이 아니다**(2026-08-25 변경) — 수락하면 `PENDING`으로 합류하고, **ADMIN이 역할을 부여해야 활성화**된다(`PATCH .../members/{memberId}/role`). 코드 유출 시 무단 가입 차단 효과 겸용. 승인 전에는 farm-scoped 전 표면이 403 F008. **폐기 정책(2026-08-19 확정)**: 농장당 활성 코드 1건 — 재발급 시 기존 코드 무효화, **멤버 제거·자발 탈퇴 시 해당 농장 활성 코드 자동 무효화**(제거·탈퇴한 멤버의 보유 코드 재합류 차단). 무효화된 코드는 F004. 코드는 DB에 SHA-256 해시로만 저장.

## 3. 핵심 엔드포인트

| Method | URL | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/auth/signup` | 공개 | SignupRequest | 201 UserResponse |
| POST | `/api/auth/login` | 공개 | LoginRequest | 200 TokenResponse |
| POST | `/api/auth/refresh` | 공개 | RefreshRequest | 200 TokenResponse |
| POST | `/api/auth/logout` | 인증 | RefreshRequest | 204 |
| POST | `/api/auth/demo-login` | 공개 | — | 200 TokenResponse (데모 계정 토큰 발급 — 자격증명 불필요, §5-데모 참조) |
| GET | `/api/users/me` | 인증 | — | 200 UserResponse |
| POST | `/api/farms` | 인증 | FarmRequest | 201 FarmResponse (생성자=**ADMIN**) |
| GET | `/api/farms` | 인증 | — | 200 List\<FarmSummaryResponse\> (내 농장) |
| GET | `/api/farms/{farmId}` | 멤버 | — | 200 FarmResponse |
| PATCH | `/api/farms/{farmId}` | **ADMIN** | FarmUpdateRequest(null=미변경. location 비우기는 1차 미지원 — 후속) | 200 FarmResponse |
| DELETE | `/api/farms/{farmId}` | **ADMIN** | — | 204 (soft delete) |
| POST | `/api/farms/{farmId}/invitations` | **ADMIN** | — | 201 InvitationResponse |
| POST | `/api/invitations/accept` | 인증 | AcceptInvitationRequest | 200 FarmResponse (**`myRole=PENDING`** — ADMIN 승인 대기) |
| GET | `/api/farms/{farmId}/members` | 멤버 | — | 200 List\<MemberResponse\> (`pending` 파생 필드 포함) |
| PATCH | `/api/farms/{farmId}/members/{memberId}/role` | **ADMIN**(+데모 차단 A007) | MemberRoleUpdateRequest{role} | 200 MemberResponse (**대기자 승인 겸용**. 마지막 ADMIN 강등 → F006) |
| DELETE | `/api/farms/{farmId}/members/{memberId}` | **ADMIN** 또는 본인 | — | 204 (**마지막 ADMIN**은 본인이든 타인이든 F006. 관리자가 여럿이면 ADMIN도 탈퇴 가능. **PENDING은 본인 취소만 허용**. 대상 미존재는 멱등 204) |
| POST | `/api/farms/{farmId}/diagnoses` | **OPERATOR** | multipart `file` | 201 DiagnosisResponse (동기) |
| GET | `/api/farms/{farmId}/diagnoses` | 멤버 | `?page&size` | 200 Page\<DiagnosisSummaryResponse\> |
| GET | `/api/farms/{farmId}/diagnoses/{diagnosisId}` | 멤버 | — | 200 DiagnosisResponse |
| POST | `/api/farms/{farmId}/prescriptions` | **OPERATOR** | PrescriptionRequest | **202** PrescriptionResponse(PENDING) |
| GET | `/api/farms/{farmId}/prescriptions/{prescriptionId}` | 멤버 | — | 200 PrescriptionResponse (폴링용) |
| GET | `/api/farms/{farmId}/prescriptions` | 멤버 | `?page&size` | 200 Page\<PrescriptionSummaryResponse\> |
| DELETE | `/api/users/me` | 인증 | WithdrawRequest{password} — **비밀번호 재확인 필수**(불일치 A002. 토큰 탈취 단독으로 비가역 삭제 불가) | 204 (soft delete — **ADMIN 농장 보유 시 409 A006**. 전 refresh 무효화+전 농장 멤버십 제거+해당 농장 활성 초대 무효화+**즉시 익명화**: email→`withdrawn-{id}@invalid`·nickname→`탈퇴회원`) |
| PATCH | `/api/farms/{farmId}/webhook` | **ADMIN** | WebhookRequest{webhookUrl?: string\|null — null=해제, https·discord.com/api/webhooks 프리픽스 검증} | 200 FarmResponse |
| GET | `/api/farms/{farmId}/diagnoses/{diagnosisId}/image` | 멤버 | — | 200 image/* 스트리밍 (원본 미보유 구 데이터 404 D004) |
| GET | `/api/farms/{farmId}/environment/today` | 멤버 | — | 200 EnvironmentTodayResponse (ai-server 프록시, 60s 캐시 허용) |

### 2026-08-20 Phase 3 확장 (FR-7·탈퇴·알림·이미지 — ai-server 무변경 원칙 해제 결정)
- **회원 탈퇴**: soft delete + `revokeAllByUserId` + 본인 farm_members 전부 삭제(각 농장 활성 초대 무효화 동반 — 기존 탈퇴 정책 재사용). ADMIN인 농장(살아있는 농장 기준)이 하나라도 있으면 **A006(409)** — 농장 삭제 후 탈퇴. 탈퇴 후 이메일 재가입 허용(partial unique index가 이미 보장).
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
- **시드**: `users.is_demo`(boolean, Flyway 신규 마이그레이션) + 앱 기동 시 idempotent 시드(init/): 데모 유저(email `demo@smartfarm.local`, nickname `데모 계정`, 랜덤 비밀번호 해시 — 비밀번호 로그인 경로 미사용) + 데모 농장 1개(ADMIN). 자격증명은 레포·문서 어디에도 평문 노출하지 않는다.
- **demo-login**: 데모 유저 조회 후 기존 토큰 발급 로직 재사용(TokenResponse). 데모 유저 존재는 시드가 보장하는 전제 — 미존재는 서버 결함이므로 C002(500)로 처리(A00x 오용 금지).
- **차단(전부 403 A007, 서버측 강제 — FE 숨김은 보조)**: 회원 탈퇴(DELETE /users/me) · 농장 생성(POST /farms) · 농장 수정/삭제(PATCH/DELETE /farms/{id}) · 웹훅 설정(PATCH /farms/{id}/webhook) · 초대코드 발급(POST /farms/{id}/invitations) · 초대코드 수락(POST /invitations/accept) · 멤버 제거/농장 나가기(DELETE 멤버 계열) · **임계치 설정(PUT /farms/{id}/env-thresholds — 2026-08-22 #52 리뷰에서 추가: 데모 방문자의 공유 농장 영속 설정 변조 차단, ADMIN 전용 write 경로 일관성)** · **알람 규칙 생성/수정/삭제(POST·PATCH·DELETE /farms/{id}/alarm-rules — 2026-08-25 #118, 같은 이유)** · **멤버 역할 변경(PATCH /farms/{id}/members/{memberId}/role — 2026-08-25 #122: 공유 계정이 농장 권한 구성을 영속 변경하는 것을 차단)**.
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
| PUT | `/api/farms/{farmId}/env-thresholds` | ADMIN | EnvThresholdsRequest | 200 EnvThresholdsResponse |

- **EnvironmentHistoryResponse** `{range, points: [{capturedAt, outdoorTemp?, outdoorHumidity?, indoorTemp?, indoorHumidity?}]}` — 다운샘플: 24h=원본(60s), 7d=30분 평균, 30d=2시간 평균(DB 집계, 빈 구간은 점 생략).
- **EnvThresholdsRequest/Response** `{enabled, indoorTempMin?, indoorTempMax?, indoorHumidityMin?, indoorHumidityMax?}`(+Response에 `updatedAt`) — 검증: min<max, 온도 -50~80, 습도 0~100(위반 C001). 저장=`farm_env_thresholds`(farm당 1행).
- **임계치 알림**: 폴러가 적재 직후 `enabled=true`인 농장 대상 **indoor 온·습도** 평가. **연속 2틱 이탈 시 발동**, 농장×항목×방향별 **쿨다운 30분**(단일 인스턴스 전제 — 메모리 상태 허용, 재시작 시 초기화 수용). 발송은 기존 디스코드 웹훅 노티파이어 컨벤션(타임아웃 5s·실패는 로그만·URL 마스킹) 준수. 신규 ErrorCode 없음(검증은 C001 재사용).
  - ⚠️ **2026-08-24 변경(#116/PR #117)**: 평가 대상이 "웹훅 설정된 농장"에서 **`enabled=true` 전체**로 확대됐다. 웹훅(알림 채널)과 알람 이벤트(영속 기록)는 다른 관심사라, 웹훅 URL 미설정 농장도 §4.12 알람 이벤트는 쌓인다. 웹훅 발송만 `webhook_url == null`이면 스킵. 단 soft delete된 농장은 계속 제외된다(`findEnabled()`가 Farm 서브쿼리로 `@SQLRestriction` 상속 — 이 서브쿼리 제거 시 삭제 농장에 알람이 무한 축적되므로 유지 필수).
- ⚠️ **2026-08-25 변경(#118/PR #121)**: 평가 주체가 `farm_env_thresholds` → **`alarm_rules`(§4.14)** 로 옮겨졌다. 이 API의 요청·응답 스펙은 **불변**이고, 저장 시 대응 파생 규칙을 제자리 upsert한다. 또 **평가 시점이 적재 성공과 분리**됐다 — 폴러는 매 틱 평가를 호출하고 `indoor`는 실제로 적재된 틱에만 전달한다(ai-server 장애 중에도 센서·장비 소스 규칙이 계속 평가되도록).
- **마이그레이션**: V9 `env_snapshots`, V10 `farm_env_thresholds` (V8은 #49 데모 선점 — **#49 먼저 머지**, out-of-order=true 확인됨). 2026-08-25: **V20**이 이 테이블의 설정을 `alarm_rules` 파생 규칙으로 이관.

## 4.7 AI 챗봇 (2026-08-23 확정, 이슈 #54·#55 — 다함 벤치마킹 3. ai-server 무변경 원칙 3번째 해제: smartfarm_ai 신규 챗 라우트)

**ai-server 신규 `POST /api/chat`** (smartfarm_ai 레포, 루프백 전용 — 조사 결과 기존 챗 엔드포인트 없음):
- 요청(Form, 기존 prescriptions 컨벤션 동일): `question: str(1~500, 필수)`, `caller_ref: str|None(≤64)`
- 처리: 기존 `retrieve(question, disease=None, k=3)` → 농업 도우미 프롬프트(병해·재배 환경 중심, 스코프 밖 질문은 짧은 안내) → qwen2.5:7b. **동시성은 기존 `inference_slot()` 세마포어 공유**(진단+처방+챗 합계 2, 포화 시 429 `{"detail"}`).
- 응답(신규 영문 스키마 — 기존 Prescription 한글 스키마 재사용 금지): `{answer: str, sources: [str], fallback: bool}` — LLM 실패 시 200+안내문+fallback=true(처방과 동일 트레이드오프).
- **이력(#66 정책 = 태깅 채택, 2026-08-23)**: `chat_messages` 테이블(id, created_at, question, answer, sources JSONB, caller_ref) — 처방 이력과 동일한 best-effort 저장(DB 미설정 시 no-op, 저장 실패가 응답을 막지 않음).

**backend (#54)**:
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/farms/{farmId}/chat` | 멤버 | ChatRequest{question(1~500)} | 200 ChatMessageResponse (동기, **타임아웃 120s**) |
| GET | `/api/farms/{farmId}/chat` | 멤버 | `?page&size` | 200 Page\<ChatMessageResponse\> (최신순) |

- **ChatMessageResponse** `{id, question, answer, sources[], fallback, createdBy, createdAt}` — backend `chat_messages`(V12, farm_id·user_id 포함)에 이력 저장 후 매핑.
- caller_ref = `svc:farm:{farmId}` 전달. ai-server 429 → **CH002(429)**, 그 외 실패/타임아웃 → **CH001(502)**.
  - **타임아웃 120s 근거(2026-08-23 #80)**: 초판 30s는 같은 로컬 LLM을 쓰는 처방 경로(120s)보다 짧아 **실사용에서 항상 타임아웃**했다(라이브 실측 30.6s 실패). 스레드 점유는 전역 동시성 가드(Semaphore 2)가 상한선을 보장하므로 타임아웃을 늘려도 고갈 위험은 없다.
  - **타임아웃 예외 매핑 주의**: JDK HttpClient 기반 팩토리는 읽기 타임아웃을 `RestClientException`이 아니라 **`CancellationException`으로 표면화할 수 있다**(운영 실측). 외부 호출 클라이언트는 이 계열까지 도메인 ErrorCode로 매핑해야 하며, 놓치면 계약이 정한 502가 아니라 **일반 500**이 나간다. 데모 계정 허용(체험 핵심 — 남용 쿼터는 #51에서 일괄).
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
| DELETE | `/api/farms/{farmId}/logs/{logId}` | **작성자 본인 또는 ADMIN** | — | 204 |

- type enum: `WATERING, FERTILIZING, PRUNING, HARVEST, PEST_CONTROL, ETC`(FE 라벨은 constants). 없음 **L001(404)**, 본인/ADMIN 아님 **L002(403)**. 데모 계정 작성 허용(컨텐츠 생성 — 진단·처방과 동일 원칙), 수정·삭제는 본인 것만이라 자연 격리.
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
| DELETE | `/api/farms/{farmId}/nutrient-recipes/{id}` | **작성자 본인 또는 ADMIN** | — | 204 |

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

## 4.10 랙·층 구조 · 장비/센서 레지스트리 (2026-08-23 확정, 이슈 #89 — 디자인 프리뷰 갭 대응 사이클 1)

**배경**: `/design-preview`(PR #86 머지)가 전제하는 도메인 중 백엔드 미보유분. 프리뷰의 `components/design-preview/mock.ts`가 유일한 스펙 소스이며, 이 절이 그것을 계약으로 승격한다.

### 계층 모델
`Farm(테넌트) > Zone(동·구역) > Rack > RackLevel(층)` 3단 계층. 프리뷰 근거: 홈 랙 배치도가 5층 × 12랙 매트릭스이고 농장 카드 메타가 "12랙 60층"(=12×5) — 일치 확인. 제어·데이터 화면의 존 필터("A동/B동/전체")가 Zone 스코프.

- **Zone** `{id, farmId, name, displayOrder}` — soft delete(`@SQLDelete`+`@SQLRestriction`, Farm 컨벤션 동일)
- **Rack** `{id, zoneId, farmId(비정규화 — 테넌트 스코프 쿼리용), code, levelCount, displayOrder}` — soft delete. `unique(zone_id, code)` (활성 행 대상 partial unique)
- **RackLevel** `{id, rackId, farmId(비정규화), levelNo, label}` — 랙 생성 시 `levelCount`만큼 자동 생성. `unique(rack_id, level_no)`. 측정값·장비가 FK로 매달리므로 별도 테이블로 둔다(정수 컬럼만 두면 `levelNo > levelCount`를 DB가 못 막음)
- **Farm 확장**: `farms ADD planted_on DATE NULL` — 프리뷰 농장 카드의 "정식 18일" 표기용. ⚠️ 작물명은 확장하지 않는다 — `CropType`은 ai-server 진단 모델 제약으로 TOMATO 전용이며, 프리뷰의 엽채류(로메인 등)는 표시 갭으로 남긴다

### 장비·센서 레지스트리
**개별 장비 단위로 저장한다.** 프리뷰 관리 화면은 제품군 집계("순환팬 A · 24 EA")로 보이지만 보정주기·최종수신·통신상태는 개체 속성이므로, 저장은 개체 단위·집계는 서버가 수행한다.

- **Device** `{id, farmId, zoneId?, rackId?, rackLevelId?, name, kind, model?, serial?, status, lastSeenAt?, calibrationDueAt?, installedOn?}` — soft delete
  - `kind`: `SENSOR | CONTROLLER | GATEWAY` (프리뷰 "센서/제어기/통신 장치")
  - `status`: `NORMAL | WARNING | FAULT | OFFLINE | OFF` (프리뷰 statusTone ok/warning/critical + 통신두절 + 제어 OFF)
    - ⚠️ **`OFF`는 2026-08-24 사이클 3에서 추가**(§4.12 초판이 `Device.status == OFF`를 전제로 썼는데 이 enum에 값이 없었다 — 계약 내부 모순이었다). `OFFLINE`(통신 두절, 장비가 응답하지 않음)과 `OFF`(정상 통신 중이나 제어로 꺼둠)는 **다른 상태**다
    - `DeviceSummaryResponse`에서의 취급은 아래 응답 DTO 절 참조(초판은 "필드를 늘리지 않고 비대칭을 수용"이었으나 2026-08-24 리뷰로 **`off` 필드를 추가**하는 쪽으로 뒤집혔다 — 옛 서술이 남아 계약 자기모순이었던 것을 정정)
  - `metrics`: **`kind=SENSOR`는 측정 지표를 1개 이상 선언한다**(§4.11 `SensorMetric` 7종의 부분집합). 2026-08-23 사이클 2에서 추가 — 초판에 이 필드가 없어 구현이 센서 1대를 **7종 복합 프로브**로 해석했고, 적재량 추정(§4.11)이 7배 틀어졌다. `CONTROLLER`/`GATEWAY`는 비운다. SENSOR인데 비었거나 비-SENSOR인데 채워졌으면 C001
  - 위치는 3개 FK 모두 nullable — 게이트웨이는 존 단위, 센서는 층 단위로 달리 매달린다. **최소 하나는 필수**(전부 null이면 C001)
  - ⚠️ **부모 FK 자동 채움**(2026-08-23 사이클 2 반영 — 초판 누락): 깊은 쪽이 주어지면 **상위를 유도해 함께 저장한다**(`rackLevelId` → `rackId`·`zoneId`, `rackId` → `zoneId`). 명시값이 함께 오면 자동 채움 대신 계층 정합성 ①②③으로 검증한다. 따라서 **저장되는 삼중조는 항상 완전하다**. 이것이 없으면 `rackLevelId`만 채운 센서의 측정값이 `zoneId`/`rackId` null로 적재되어 **존·랙 스코프 조회에서 조용히 누락**된다(§4.11이 위치를 그대로 복사하기 때문)
  - `serial`은 농장 스코프 partial unique(활성 행), null 허용

### 엔드포인트
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/zones` | 멤버 | — | 200 `ZoneTreeResponse` (존+랙+층 트리 — 랙 도면 렌더용 1회 조회) |
| POST | `/api/farms/{farmId}/zones` | ADMIN(데모 차단) | `{name, displayOrder?}` | 201 `ZoneResponse` |
| PATCH | `/api/farms/{farmId}/zones/{zoneId}` | ADMIN(데모 차단) | `{name?, displayOrder?}` | 200 `ZoneResponse` |
| DELETE | `/api/farms/{farmId}/zones/{zoneId}` | ADMIN(데모 차단) | — | 204 (하위 랙·층 함께 soft delete). **하위에 장비 잔존 시 R004 거부** |
| POST | `/api/farms/{farmId}/zones/{zoneId}/racks` | ADMIN(데모 차단) | `{code, levelCount(1~50), displayOrder?}` | 201 `RackResponse` (층 자동 생성) |
| PATCH | `/api/farms/{farmId}/racks/{rackId}` | ADMIN(데모 차단) | `{code?, levelCount?, displayOrder?}` | 200 `RackResponse` |
| DELETE | `/api/farms/{farmId}/racks/{rackId}` | ADMIN(데모 차단) | — | 204 (하위 층 함께 soft delete). **하위에 장비 잔존 시 R004 거부** |
| GET | `/api/farms/{farmId}/devices` | 멤버 | `?kind=&status=&q=&zoneId=` (q=장비명 부분일치) | 200 `DeviceListResponse` |
| GET | `/api/farms/{farmId}/devices/summary` | 멤버 | — | 200 `DeviceSummaryResponse` (KPI 5종 + 제품군별 집계) |
| POST | `/api/farms/{farmId}/devices` | ADMIN(데모 차단) | `DeviceRequest` | 201 `DeviceResponse` |
| PATCH | `/api/farms/{farmId}/devices/{deviceId}` | ADMIN(데모 차단) | `DeviceRequest`(부분) | 200 `DeviceResponse` |
| DELETE | `/api/farms/{farmId}/devices/{deviceId}` | ADMIN(데모 차단) | — | 204 |

- **ZoneTreeResponse** `{zones: [{id, name, displayOrder, racks: [{id, code, levelCount, displayOrder, levels: [{id, levelNo, label}]}]}]}`
- **DeviceSummaryResponse** `{total, normal, warning, faultOrOffline, off, calibrationDueSoon, byModel: [{name, kind, count, status}]}` — `calibrationDueSoon`=30일 이내
  - ⚠️ **`off` 필드는 2026-08-24 사이클 3에서 추가**(리뷰 반영): 없으면 비상 정지 직후 `{total:60, normal:0, warning:0, faultOrOffline:0}`이 되어 **농장 전체가 정지했는데 화면에는 "이상 없음"으로 읽힌다**(렌더 버그와 구분 불가). `total = normal + warning + faultOrOffline + off`가 성립해야 한다
- ⚠️ **`PATCH /devices/{deviceId}`의 `status`로 `OFF`를 설정할 수 없고, 이미 `OFF`인 장비의 `status`는 아예 바꿀 수 없다**(양쪽 C001, 2026-08-24 사이클 3 리뷰 반영 — 초판은 "설정 금지"만 적어 **되살리기(OFF→NORMAL)가 열려 있었다**. 규칙은 "**OFF 장비의 상태는 제어 경로로만 바꾼다**"다): `OFF`는 §4.12가 정의한 **제어 조작의 결과**이지 레지스트리 편집 대상이 아니다. ⚠️ 단 **`status`를 생략한 PATCH(이름·위치·보정주기 편집)는 OFF 장비에도 허용**하고, OFF가 아닌 장비의 관측 상태 갱신(`NORMAL↔WARNING↔FAULT↔OFFLINE`)도 그대로 허용한다 — 아니면 꺼둔 장비를 영영 수정할 수 없다. PATCH 경로에는 모드 게이트(CT003)·대기 큐 2단계·존 단위 락·감사 이력이 **전부 없어서**, 허용하면 비상 정지를 걸어둔 장비를 감사 없이 되살릴 수 있고 §4.12 동시성 3(존 단위 직렬화)이 우회된다. 끄기/켜기는 제어 경로로만 한다
- **`levelCount` 축소 시**: 잘려나가는 층에 장비가 매달려 있으면 **R004로 거부**(조용한 데이터 유실 방지). 측정 이력만 있는 경우는 층을 soft delete하고 이력은 보존
- **구조 삭제 시에도 동일 규칙**(2026-08-23 리뷰 반영 — 계약 초판 누락): 랙·존 삭제도 하위에 활성 장비가 있으면 **R004로 거부**한다. 초판은 `levelCount` 축소에만 R004를 걸어, `DELETE /racks/{id}`가 같은 결과를 검사 없이 통과하는 우회로가 있었다(장비가 soft delete된 층을 참조한 채 살아남아 랙 도면에서는 사라지고 `devices/summary` 집계에는 계속 잡힘)
- **장비 위치 FK 3종의 부모-자식 정합성**(2026-08-23 리뷰 반영 — 계약 초판 누락): `zoneId`/`rackId`/`rackLevelId`는 각각 농장 소속인 것만으로 부족하고 **서로의 계층 관계가 일치해야 한다**. 규칙은 **쌍 2개가 아니라 전이까지 3개**다(2차 리뷰 반영 — 1차 보정도 쌍만 적어 불완전했다): ① `rack.zoneId == zoneId` ② `level.rackId == rackId` ③ **전이** — `rackId`를 생략해도 `level → rack → zone`을 따라가 `zoneId`와 대조한다. ③이 없으면 `{zoneId: A동, rackId: null, rackLevelId: B동 랙의 층}`이 두 쌍 검사를 모두 skip하고 통과한다(`rackId == null`이면 ①은 안 돌고 ②는 가드에 막힘). 불일치는 C001. PATCH는 부분 수정이므로 **요청값과 기존 엔티티를 병합한 최종 상태**로 검증한다. ⚠️ §4.11의 `SensorReading`이 이 3종을 device에서 유도해 비정규화하므로, 모순된 삼중조는 측정값 테이블로 그대로 복제되어 랙×층 매트릭스 집계를 오염시킨다
- **⚠️ 후속(미도입) — 목록 페이지네이션**: `GET /zones`·`GET /devices`·`GET /devices/summary`는 현재 농장 전체를 반환한다. 이 레포는 farm-scoped 컬렉션에 `Pageable`이 이미 관례(`PrescriptionController` `@PageableDefault(size=20)` 등)이나 이번 사이클은 계약·DTO 변경 범위가 커져 **후속 이슈로 분리**한다. 자기 테넌트 ADMIN이 스스로 데이터를 부풀려야 성립하고 현 데이터 규모(농장당 랙 12·장비 수십)에서는 실현되지 않으나, **리소스 생성 상한 부재와 묶어 함께 처리할 것**

### ⚠️ 테넌트 격리 (필수)
`zoneId`·`rackId`·`deviceId`는 전부 **path 입력값 취급**. `FarmAccessGuard.requireMember/requireOwner`로 농장 멤버십을 재검증한 뒤, **해당 리소스가 그 농장 소속인지 반드시 재확인**한다(다른 농장의 rackId를 자기 farmId 경로에 끼워 넣는 cross-tenant IDOR 차단). 미소속 리소스는 존재를 유추당하지 않도록 **404(R00x/E001)** 로 응답한다. 격리 테스트 동반 필수.

### ErrorCode (신규)
| 코드 | HTTP | 의미 |
|---|---|---|
| R001 | 404 | 존 없음(타 농장 소속 포함) |
| R002 | 404 | 랙 없음(타 농장 소속 포함) |
| R003 | 404 | 층 없음(타 농장 소속 포함) |
| R004 | 409 | 랙 구조 변경 불가(층 축소 시 하위 장비 잔존·랙 코드 중복) |
| E001 | 404 | 장비 없음(타 농장 소속 포함) |
| E002 | 409 | 장비 시리얼 중복(농장 내) |

- **마이그레이션**: V14 `zones`·`racks`·`rack_levels`·`devices` + `farms.planted_on` · **V16** `device_metrics`(사이클 2에서 추가 — V15는 `sensor_readings`가 선점)

## 4.11 센서 측정값 (2026-08-23 확정, 이슈 #90 — 디자인 프리뷰 갭 대응 사이클 2)

### 기존 `env_snapshots`와의 관계 — 대체가 아니라 공존
`env_snapshots`(V9)는 **농장 구분이 없는 ai-server 단일 하우스 실측**(외기 KMA + 내부 제어값)이다. 여기에 farmId를 소급 부여하면 데이터 의미가 왜곡되고 라이브인 환경 대시보드(#22)·시계열 차트(#53)가 깨진다. 따라서 **기존 테이블·API·폴러는 무변경**으로 두고, 층·장비 스코프의 신규 스트림을 별도로 세운다.

| 스트림 | 출처 | 스코프 | 지표 | 용도 |
|---|---|---|---|---|
| `env_snapshots` (기존) | ai-server 실측 | 전역 단일 | 실내외 온·습도 | 환경 대시보드, 임계치 알림 |
| `sensor_readings` (신규) | **가상 장비 시뮬레이터** | 농장>존>랙>층 | 7종 | 랙 도면, 층별 비교, 다지표 그래프 |

### SensorReading
`{id, farmId, deviceId(FK), zoneId?, rackId?, rackLevelId?, metric, value, measuredAt}`
- 위치 3종은 `device`에서 유도 가능하지만 **조회 성능을 위해 비정규화**(멀티테넌트 룰의 `farmId` 비정규화 컨벤션과 동일)
- ⚠️ **비정규화의 진실 소스 = 적재 시점 스냅샷**(사이클 1 회고 반영 — 파생값 규칙 누락이 P1 2건의 근인이었다): 위치 3종은 **적재 순간 device의 값을 복사해 고정**한다. 이후 device가 다른 층으로 옮겨가도 **과거 readings는 소급 갱신하지 않는다** — "그 측정이 어디서 이뤄졌는가"가 사실이고, 현재 위치로 덮으면 이력이 거짓이 된다. 조회 시 device와 join해 위치를 다시 유도해서도 안 된다
- ⚠️ **§4.10 계층 정합성의 상속**: 적재 시 복사하는 삼중조는 §4.10의 규칙 ①②③을 이미 만족한 값이다. 시뮬레이터·수집기는 device의 값을 **그대로 복사만** 하고 임의로 조합하지 않는다(조합하는 순간 §4.10이 막은 모순 삼중조가 여기서 재생산된다)
- ⚠️ **부모 soft delete와의 관계**: §4.10은 "장비 잔존 시 삭제 거부(R004), 측정 이력만 있으면 층을 soft delete하고 이력 보존"으로 정했다. 따라서 **soft delete된 존·랙·층을 참조하는 readings가 정상적으로 존재한다**. 조회 API는 **활성 구조만 렌더**하되(`ReadingMatrixResponse`는 살아있는 랙·층만), 이력 자체는 지우지 않는다. device soft delete도 동일 — readings는 보존되고 시뮬레이터 대상에서만 빠진다
- **`source` 컬럼**(`SIMULATED | DEVICE`): 시뮬레이터를 끄고 실기기를 붙이면 한 테이블에 두 출처가 섞인다. **행 단위로 출처를 남기지 않으면 사후 구분이 불가능**하므로 컬럼으로 둔다. 응답의 `simulated`는 조회 범위 내 `source`의 집계 결과이지 전역 플래그가 아니다
- `metric` enum: `TEMPERATURE | HUMIDITY | CO2 | EC | PH | PPFD | POWER` (프리뷰 데이터 화면 항목 7종)
- 인덱스: `(farm_id, metric, measured_at desc)`, `(rack_level_id, metric, measured_at desc)`
- **보존 90일** + purge 스케줄러 (`EnvSnapshotPurgeScheduler` **구조**는 재사용하되 **상수는 재산정한다**)
- ⚠️ **purge 처리량 ≥ 유입량**(2026-08-23 사이클 2 리뷰 반영 — 계약 초판이 "패턴 그대로"라고만 적어 상수까지 복사됐다): `env_snapshots`의 유입은 60s × 1행 = **1,440행/일**이라 배치 20×1000=20,000행/일로 14배 여유였다. `sensor_readings`는 유입이 **120~432배**(농장당 최대 300행/틱 × 1440틱)라 같은 상수로는 **보존 90일이 절대 성립하지 않고 매일 순증**한다. 삭제 상한은 반드시 `max-rows-per-tick × 1440 × 예상 농장수 × 2` 이상으로 잡고, **산정 근거를 코드 주석에 계산식으로 남긴다**. 대안: purge 주기 단축 또는 `measured_at` 월 단위 파티셔닝 + `DROP PARTITION`(시계열 정석, vacuum 부담도 해소)
- **purge 전용 인덱스**: `(measured_at)` 단독 인덱스가 필요하다 — 조회용 복합 인덱스 2종은 선행 컬럼이 달라 `WHERE measured_at < :cutoff`에 쓸 수 없다(V9가 `idx_env_snapshots_captured_at` 단독을 둔 이유와 동일)
- **중복 적재 차단**: `(device_id, metric, measured_at)` unique — 다중 인스턴스나 `fixedDelay` 드리프트로 같은 분에 두 번 tick하면 `device_avg` CTE가 중복을 **오류 없이 조용히 평균에 흡수**한다

### ⚠️ 가상 장비 시뮬레이터 (실기기 부재)
실기기·실센서가 없으므로 측정값은 **백엔드가 생성한다**. `docs/STATUS.md`의 기존 "원격제어·EC/pH 실시간 제외(실기기 부재)" 결정을 **시뮬레이션 전제로 한정 해제**한다.

- `@Scheduled(fixedDelay=60s)` — `kind=SENSOR`이고 `status`가 **`OFFLINE`·`OFF`가 아닌** 장비마다 1틱 생성. **생성 지표는 그 장비의 `metrics` 선언분만**(§4.10) — 전 지표를 일괄 생성하지 않는다
  - ⚠️ **`OFF` 제외는 §4.12-4(비상 정지가 제어기만 끈다)와 짝을 이룰 때만 안전하다**(2026-08-24 리뷰 반영 — 초판은 `OFFLINE`만 적어 실제 동작과 어긋났다). 둘 중 하나만 바꾸면 **농장 전체 측정 스트림이 영구 정지**한다(비상 정지가 센서까지 끄는데 시뮬레이터가 OFF 센서를 제외하는 조합). 어느 한쪽을 손댈 때 반드시 다른 쪽을 함께 검토할 것
- 값 = **일주기 기저(sin) + 층별 오프셋 + 결정적 노이즈**. 노이즈 시드는 `(deviceId, measuredAt 분)` 해시 — 재기동해도 파형이 튀지 않고, 테스트에서 재현 가능
- `smartfarm.simulator.enabled` 플래그(기본 true, 운영에서 실기기 연동 시 false). **비활성 시 폴러 자체가 뜨지 않는다**
- ⚠️ **적재량 상한**(#91 교훈 선반영 + 사이클 2 정정): 상한은 **센서 대수가 아니라 1틱당 생성 행 수**로 센다 — 센서 대수로 세면 장비당 지표 수만큼 곱해져 추정이 빗나간다(초판이 이 실수를 했다: 센서 200개 상한을 두고 행 수는 1,400행/틱이 됐다). **농장당 1틱 최대 300행**, 초과분은 생성하지 않고 **WARN 로그**(조용히 잘라내지 않는다).
  - 참고 추정: 프리뷰 규모(12랙×5층=60층, 층당 온습도 센서 1대=2지표) → 120행/틱 → 60s·90일 = **15.5M행**. 지표 선언 없이 7종 복합 프로브로 두면 같은 규모가 54M행이 된다
  - ⚠️ **전역 상한도 필요하다**(사이클 2 리뷰 반영): 농장당 상한만으로는 **농장 수만큼 곱해진다.** 농장 생성 상한이 아직 없으므로(#91) 계정 1개로 농장을 대량 생성하면 일회성 쓰기가 **영구적·자율적 백그라운드 쓰기로 증폭**된다. 1틱 전체 생성 행 수에 전역 상한을 둔다. ⚠️ 이로써 **#91이 페이지네이션·생성 상한을 유예하며 든 근거("자기 테넌트에서 스스로 부풀려야 성립하고 현 규모에선 실현되지 않는다")는 무효**가 됐다 — #91의 위험 등급을 상향한다
  - 시뮬레이터 tick은 **농장 단위로 트랜잭션을 끊는다** — 전 농장 장비와 생성 행을 한 트랜잭션·한 힙에 올리지 않는다
- 응답 DTO에 `simulated: true` 를 실어 프론트가 화면에 시뮬레이션임을 표기할 수 있게 한다 — 실데이터인 척하지 않는다

### 엔드포인트
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/readings/series` | 멤버 | `?metrics=TEMPERATURE,EC`(최대 4, 초과 C001) `&range=24h\|7d\|30d` `&scope=farm\|zone:{id}\|rack:{id}\|level:{id}` | 200 `ReadingSeriesResponse` |
| GET | `/api/farms/{farmId}/readings/latest` | 멤버 | `?metric=&zoneId=` | 200 `ReadingMatrixResponse` (랙×층 최신값 — 랙 도면 셀 상태) |
| GET | `/api/farms/{farmId}/readings/level-summary` | 멤버 | `?rackId=&range=24h\|7d\|30d` | 200 `LevelSummaryResponse` (층별 평균 + 목표 대비 편차 — 데이터 화면 비교표) |

- **다운샘플**: 24h=원본(60s) / 7d=30분 평균 / 30d=2시간 평균 — **§4.6 규칙 그대로 재사용**(DB 집계, 빈 구간은 점 생략)
- **ReadingSeriesResponse** `{range, scope, simulated, series: [{metric, unit, points: [{at, value}]}]}`
- **LevelSummaryResponse** `{rackId, code, range, simulated, levels: [{levelNo, label, metrics: [{metric, unit, average, deviationPercent, state}]}]}` — 층×지표 그리드(2026-08-23 사이클 2에서 계약 승격). 요청에 `metric` 파라미터가 없으므로 층마다 보유 지표 전부를 싣는다. 프리뷰 데이터 화면의 층별 비교표(층 / 온도 / 습도 / EC / PPFD / 편차)와 1:1 대응
- **ReadingMatrixResponse** `{metric, unit, simulated, racks: [{rackId, code, levels: [{levelNo, value?, measuredAt?, state}]}]}` — `state`: `OK | WARNING | CRITICAL | IDLE`(프리뷰 `CellState`와 1:1).
  - ⚠️ **신선도 상한**(사이클 2 리뷰 반영 — 초판 누락): 최신값이라도 **tick 주기 × 5보다 오래됐으면 `IDLE`**로 떨어뜨리고 값을 현재값으로 렌더하지 않는다. 장비를 철거해도 readings는 보존되므로(§4.10), 상한이 없으면 **두 달 전 값이 "지금 22°C, 정상"으로 표시**되어 센서 부재 자체를 인지할 수 없다. `measuredAt`도 함께 실어 프론트가 판단할 수 있게 한다
- ⚠️ **`scope=farm`과 soft delete된 구조**: zone/rack/level 스코프는 `findByIdAndFarmId`가 삭제 구조를 404로 막지만, `scope=farm`은 `farm_id`만으로 직접 집계하므로 **삭제된 층의 과거 이력이 농장 평균에 계속 섞인다.** 동일 테넌트라 정보 노출은 아니나 "랙을 지웠는데 차트가 안 변한다"가 된다 — **활성 구조로 조인해 제외**한다(`latest`·`level-summary`와 동작을 일치시킨다) 판정 기준은 `farm_env_thresholds`가 아직 온·습도만 다루므로 **1차는 지표별 상수 기본범위**를 쓰고, 임계치 확장은 사이클 3(알람)으로 미룬다
- ⚠️ **스코프 파라미터도 리소스 소속을 검증한다**(사이클 1 P3 반복 방지): `scope=zone:{id}`·`rack:{id}`·`level:{id}`의 id와 `?zoneId=`·`?rackId=`는 **query 파라미터라도 path와 동일하게 취급**한다 — 농장 소속을 확인하고 미소속은 **404(R001~R003)**. 빈 배열로 뭉개지 않는다(사이클 1의 `listDevices` `zoneId`가 정확히 이 불일치로 지적됐다). `scope` 문자열 형식 위반은 C001
- ⚠️ **집계의 이중성 정의**: 한 층에 같은 metric 센서가 여러 대 있을 수 있다. 집계는 **① 같은 시각 버킷 내 device 간 평균 → ② 시간 버킷 평균** 순서로 한다(순서를 안 정하면 센서 대수가 많은 층에 가중치가 붙는다). `level-summary`의 "층별 평균"도 동일
- ⚠️ **응답 크기 상한**: `series`는 24h 원본이 metric당 최대 1440점 × 4 metric = 5760점이다. **metric 4개 상한은 이미 있으나 스코프 상한이 없다** — `scope=farm`이면 전 층이 섞인다. `scope=farm`은 **층 간 평균 1계열로 축약**하고, 층별 개별 계열이 필요하면 `level-summary`를 쓴다. `level-summary`는 `rackId` **필수**(생략 시 C001 — 농장 전체 층을 한 번에 반환하지 않는다)
- **unit 매핑**(상수, 서버가 내려준다): `TEMPERATURE=°C · HUMIDITY=% · CO2=ppm · EC=dS/m · PH=pH · PPFD=µmol/m²/s · POWER=kW`
- **신규 ErrorCode 없음** — 검증 실패는 C001, 스코프 리소스 부재는 §4.10의 R001~R003 재사용. 데이터 없음은 오류가 아니라 빈 배열
- **마이그레이션**: V15 `sensor_readings`(`source` 컬럼 포함) · **V17** purge용 `(measured_at)` 인덱스 + `(device_id, metric, measured_at)` unique

## 4.12 제어 도메인 (2026-08-24 확정, 이슈 #100 — 디자인 프리뷰 갭 대응 사이클 3)

**사이클 등급: Critical** — 적용 대기 큐의 일괄 커밋, 자동/수동 모드 전이, 비상 정지가 전부 상태 전이 + 동시성이다.

### ⚠️ 시뮬레이션 전제
실기기가 없으므로 제어는 **§4.11 가상 장비 시뮬레이터에 작용**한다. `docs/STATUS.md`의 "원격제어 제외(실기기 부재)" 결정은 §4.11과 동일하게 **시뮬레이션 전제로만 한정 해제**한다. 응답에 `simulated: true`를 실어 실제 기기를 제어하는 척하지 않는다.

### 모델
- **ControlMode** `{id, farmId, zoneId, mode(AUTO|MANUAL), updatedAt, updatedBy}` — **존당 1행**(unique). 미설정 존은 `AUTO` 기본으로 간주(행 생성 전에도 조회가 성립해야 한다)
- **ControlSetpoint** `{id, farmId, zoneId, metric, targetValue, updatedAt, updatedBy}` — **존×지표당 1행**(unique). `metric`은 §4.11 `SensorMetric` 중 제어 가능한 것만(`TEMPERATURE|HUMIDITY|CO2|PPFD` — 프리뷰 목표값 4종). EC/PH/POWER는 제어 대상이 아니다
- **ControlChange**(적용 대기 큐) `{id, farmId, zoneId, kind(SETPOINT|DEVICE), metric?, deviceId?, fromValue, toValue, status(PENDING|APPLIED|DISCARDED), createdBy, createdAt, appliedAt?, appliedBy?}` — 큐 = `status=PENDING` 목록
  - ⚠️ **큐는 서버에 저장한다.** 프리뷰는 "로컬 큐"로 구현했으나 그건 목업 제약이다. 실제로는 새로고침·다중 사용자·다중 탭에서 큐가 보존·공유돼야 한다
- **ControlApplyLog** `{id, farmId, zoneId, summary, itemCount, appliedBy, appliedAt}` — 적용 이력(프리뷰 "최근 적용")

### 운전 모드와 허용 조작 (프리뷰 상호작용 절)
| 모드 | 목표값 편집 | 장비 직접 토글 |
|---|---|---|
| `AUTO` | **허용** | **거부(CT003)** — 자동 제어가 장비를 관리한다 |
| `MANUAL` | 거부(CT003) | **허용** |

프리뷰의 "자동 운전 OFF 시 목표값 카드 비활성"이 이 표의 근거다.

### 엔드포인트
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/zones/{zoneId}/control` | 멤버 | — | 200 `ControlStateResponse` (모드 + 목표값 4종 + 장비 상태 + 대기 큐 + 최근 이력) |
| PUT | `/api/farms/{farmId}/zones/{zoneId}/control/mode` | 멤버(데모 차단) | `{mode}` | 200 `ControlStateResponse` |
| POST | `/api/farms/{farmId}/zones/{zoneId}/control/changes` | 멤버(데모 차단) | `ControlChangeRequest` | 201 `ControlChangeResponse` (큐에 적재만 — **장비에 즉시 반영하지 않는다**) |
| DELETE | `/api/farms/{farmId}/zones/{zoneId}/control/changes/{changeId}` | 작성자 본인 또는 ADMIN(데모 차단) | — | 204 (개별 취소 → `DISCARDED`) |
| DELETE | `/api/farms/{farmId}/zones/{zoneId}/control/changes` | 멤버(데모 차단) | — | 204 (전체 되돌리기) |
| POST | `/api/farms/{farmId}/zones/{zoneId}/control/apply` | 멤버(데모 차단) | `{expectedChangeIds: [..]}` | 200 `ControlApplyResponse` |
| POST | `/api/farms/{farmId}/control/emergency-stop` | ADMIN(데모 차단) | — | 200 `EmergencyStopResponse` (농장 전체) |

### ⚠️ 동시성 (Critical — 이 절이 이 사이클의 핵심)
1. **일괄 적용의 낙관적 검증**: `apply`는 `expectedChangeIds`를 **필수**로 받는다. 현재 PENDING 집합과 다르면 **CT005로 거부**하고 최신 큐를 응답에 실어 재확인시킨다. 사용자 A가 큐를 보고 있는 사이 B가 항목을 추가·삭제했는데 A의 "적용"이 그것까지 함께 반영해버리는 사고를 막는다
2. **적용은 단일 트랜잭션**: PENDING → APPLIED 전이, `ControlSetpoint`/`Device.status` 갱신, `ControlApplyLog` 기록이 **원자적**이어야 한다. 부분 적용 상태를 남기지 않는다
3. **존 단위 직렬화**: 같은 존에 대한 `apply`/`emergency-stop`은 동시에 실행되면 안 된다. **존 행을 `@Lock(PESSIMISTIC_WRITE)`로 잠근다**(#91의 TOCTOU 교훈 — 읽고-쓰기 사이에 락이 없으면 검사 결과가 무효가 된다). 락 대상은 `ControlMode` 행(존당 1행이라 자연스러운 잠금 지점)
4. **비상 정지 우선**: `emergency-stop`은 전 존의 **`kind=CONTROLLER` 장비만** OFF + 모드를 `MANUAL`로 내리고 **모든 PENDING 큐를 `DISCARDED`로 폐기**한다. 정지 후 남아있던 큐가 나중에 적용되면 안 된다
   - ⚠️ **센서·게이트웨이는 끄지 않는다**(2026-08-24 리뷰 반영 — 계약 초판이 `kind`를 구분하지 않아 **자기모순**이었다). 초판대로 전 장비를 끄면 §4.11 시뮬레이터가 OFF 센서를 tick 대상에서 제외하므로 **농장 전체 측정 스트림이 영구 정지**하고 벌크 복구 경로도 없다. 무엇보다 이 절의 시뮬레이터 연동이 "OFF인 제어기가 있는 존은 **자연 표류**"라고 쓰는데, **표류하려면 센서가 계속 측정해야 한다**. 정지의 목적은 액추에이터를 멈추는 것이지 관측을 멈추는 것이 아니다
5. 상태 전이는 **엔티티 메서드로 캡슐화**한다(`PrescriptionStatus.isTerminal()` 선례). `APPLIED`/`DISCARDED`는 종료 상태 — 재전이 금지

### ⚠️ 계층·캐스케이드 (§4.10 상속)
- `zoneId`·`deviceId`는 path/body 입력값 취급. §4.10의 계층 정합성 ①②③과 **소속 재확인 후 미소속 404** 규약을 그대로 따른다
- **장비는 그 존 소속이어야 한다** — `DEVICE` 종류 변경의 `deviceId`가 대상 존 하위인지 검증(§4.10 자동 채움으로 삼중조가 완전하므로 `device.zoneId == zoneId` 비교로 충분)
- **통신 두절 장비 조작 거부**: `device.status == OFFLINE`이면 큐 적재 시점에 **CT002로 거부**한다(적용 시점이 아니라 적재 시점 — 프리뷰가 "통신 두절 장비 클릭 시 안내"로 즉시 피드백한다)
- **캐스케이드**: 존·랙·장비가 soft delete되면 그것을 참조하는 **PENDING 큐 항목은 `DISCARDED`로 폐기**한다. §4.10이 활성 장비 잔존 시 삭제를 R004로 막으므로 장비 경유 경로는 대부분 차단되지만, 큐가 참조하는 대상이 사라지는 경로를 열어두면 적용 시점에 고아 참조가 된다. `ControlSetpoint`는 존과 함께 soft delete하고, **`ControlApplyLog`는 감사 이력이므로 보존**한다

### ⚠️ 시뮬레이터 연동 (§4.11과의 접점)
적용된 제어가 **측정값에 실제로 반영되어야** 데모가 성립한다.
- `ControlSetpoint.targetValue`가 §4.11 시뮬레이터의 **기저값(일주기 sin의 중심)을 대체**한다. 미설정 지표는 기존 상수 기저 유지
- 수렴은 즉시가 아니라 **tick당 일정 비율**(예: 목표와 현재의 차이 × 0.2)로 근접시킨다 — 즉시 점프하면 그래프가 계단이 되어 시계열 화면이 부자연스러워진다
- `Device.status == OFF`인 제어기(`kind=CONTROLLER`)가 있는 존은 해당 지표를 목표로 수렴시키지 않는다(제어기가 꺼졌으니 자연 표류)
- ⚠️ **`mode == MANUAL`인 존도 수렴 대상에서 제외한다**(2026-08-24 리뷰 반영 — 초판 누락): 해제 판정이 "꺼진 제어기의 존재"에만 의존하면 **제어기가 없는 존과 제어기가 OFFLINE인 존은 비상 정지 후에도 계속 목표값으로 수렴한다**. UI는 "정지 완료"를 표시하는데 그래프는 계속 끌려가므로 안전 기능의 의미가 **존의 장비 구성에 따라 달라진다**. 무엇보다 이 절의 운전 모드 표가 이미 "MANUAL에서는 목표값 편집 거부(CT003)"로 정했으므로, MANUAL 존이 목표에 끌려다니는 것은 **새 규칙이 아니라 계약 정합화**다. 비상 정지가 전 존을 MANUAL로 내리므로 이 규칙 하나로 장비 구성과 무관하게 수렴이 멈춘다
- ⚠️ **`source`는 여전히 `SIMULATED`다**(§4.11). 제어가 붙었다고 실측이 되는 게 아니다

### 상한 (사이클 2 교훈 선반영)
- **PENDING 큐 상한: 존당 50건**(초과 시 CT004). 무제한이면 `apply` 트랜잭션이 무한정 커진다
- **`ControlApplyLog` 보존 90일** + purge. ⚠️ **purge 상한은 유입량 기준으로 산정하고 근거를 주석에 계산식으로 남긴다** — 사이클 2에서 `EnvSnapshotPurgeScheduler` 상수를 유입량 120배 차이에 그대로 복사해 P1이 났다. 적용 로그는 사용자 조작 기반이라 유입이 훨씬 적지만(하루 수백 건 수준), **그 산정 자체를 근거와 함께 남기는 것이 요구사항**이다

### 계약 초판 누락분 (2026-08-24 구현 중 드러나 확정)
- **모드 변경 시 새 모드에서 허용되지 않는 PENDING은 폐기**한다(`DISCARDED`). 남겨두면 `apply`가 영구히 CT003으로 실패하는 교착이 된다
- **목표값 sanity 범위**: 물리적으로 불가능한 값이 시뮬레이터 기저가 되는 것을 막기 위해 지표별 넉넉한 입력 범위를 검증한다(위반 C001)
- **개별 취소의 권한 위반**은 전용 CT 코드를 두지 않고 기존 **A005**(403)를 쓴다
- **랙 삭제 캐스케이드는 불필요**: 큐가 참조하는 대상은 존·장비뿐이고, §4.10 R004가 활성 장비 잔존 랙의 삭제를 막으므로 폐기 대상이 생기지 않는다
- **존 soft delete 시 `control_modes` 행은 남긴다**: 그 행이 존 단위 잠금 지점이라 지우면 락 대상이 사라진다. 존 id는 재사용되지 않고 전 API 표면이 R001로 막으므로 도달 불가하다

### ⚠️ 후속으로 남긴 결정 (2026-08-24 리뷰)
- **`WARNING`/`FAULT`가 OFF로 덮이면 복원되지 않는다**: 이 코드베이스에는 `status`를 관측으로 재도출하는 경로가 없어(수동 PATCH가 유일) 비상 정지 1회로 장애 정보가 소실되고 `faultOrOffline`이 0이 된다 — 정지 직후가 장애 파악이 가장 필요한 순간이다. `control_changes.fromValue`에 이전 상태가 이미 저장되므로 켜기 시 복원이 가능하나 정책 결정이 필요해 **후속 이슈로 분리**한다
- **큐 폐기의 행위자 미기록**: `ControlChange`에 `discardedBy`/`discardedAt`이 없어, 전체 되돌리기·모드 변경 연쇄 폐기로 **타인의 항목을 지운 주체를 추적할 수 없다**. 모델 필드 추가라 후속

### ErrorCode (신규)
| 코드 | HTTP | 의미 |
|---|---|---|
| CT001 | 404 | 제어 변경 항목 없음(타 농장·타 존 소속 포함) |
| CT002 | 409 | 통신 두절 장비는 조작할 수 없음 |
| CT003 | 409 | 현재 운전 모드에서 허용되지 않는 조작(AUTO에서 장비 직접 토글 / MANUAL에서 목표값 편집) |
| CT004 | 409 | 적용 대기 큐 상한 초과(존당 50건) |
| CT005 | 409 | 대기 큐가 변경됨 — 최신 큐로 재확인 후 다시 적용(낙관적 검증 실패) |

- **마이그레이션**: **V18** `control_modes`·`control_setpoints`·`control_changes`·`control_apply_logs`

## 4.13 알람 이벤트 (2026-08-24 확정, 이슈 #116 — 미구현 도메인 정리 1순위)

- **목적**: 임계치 이탈을 **영속 기록**해 알람 화면·TopBar "미확인 알람 N건" 배지의 데이터 소스가 된다. §4.6 임계치 알림(웹훅=알림 채널)과 **관심사 분리** — 웹훅 미설정 농장도 이벤트는 쌓인다.
- **상태 전이**: `UNACKNOWLEDGED → ACKNOWLEDGED → RESOLVED`. 역방향·건너뛰기 금지(AL002). 정상 복귀 감지 시 시스템이 **자동 RESOLVED**(`resolvedBy=null`, 타임라인에 `note="자동 해소"`).
- **멱등성**: 같은 `farm × metricKey` 조합으로 미해결(UNACKNOWLEDGED/ACKNOWLEDGED) 이벤트가 있으면 **새로 만들지 않는다**. 애플리케이션 조회(1차) + DB partial unique index `(farm_id, metric_key) WHERE status<>'RESOLVED'`(2차).
  - `metricKey` = **`RULE_{ruleId}`**(2026-08-25, #118 — 구 형식 `{EnvMetric}_{EnvDirection}`은 V20이 재매핑). ⚠️ `{source}_{metric}_{scope}` 류의 조합 키를 쓰면 **같은 스코프·지표를 다른 임계값으로 보는 두 규칙**(예: CRITICAL EC>3.2 + WARNING EC>2.8)이 같은 키를 만들어 한쪽 알람이 조용히 삼켜진다. rule id는 전역 유일이라 항상 정합.
  - 규칙이 삭제·비활성되면 열린 알람은 **그 시점에 자동 해소**된다(고아 방지 3경로: 규칙 삭제·규칙 비활성·파생 규칙 비활성).
- **동시성**: `@Version` 낙관적 락. 충돌 시 **409 C005**(공통 코드 — `@Version`을 쓰는 모든 엔티티에 적용).

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/alarm-events` | 멤버 | `?status=&severity=&page=&size=` | 200 `Page<AlarmEventResponse>` |
| GET | `/api/farms/{farmId}/alarm-events/{id}` | 멤버 | — | 200 AlarmEventDetailResponse(+타임라인) |
| PATCH | `/api/farms/{farmId}/alarm-events/{id}/acknowledge` | **OPERATOR** | — | 200 AlarmEventResponse |
| POST | `/api/farms/{farmId}/alarm-events/{id}/resolve` | **OPERATOR** | — | 200 AlarmEventResponse |
| POST | `/api/farms/{farmId}/alarm-events/{id}/memo` | **OPERATOR** | AlarmMemoRequest | 200 AlarmEventDetailResponse |
| POST | `/api/farms/{farmId}/alarm-events/acknowledge-all` | **OPERATOR** | — | 200 AlarmAcknowledgeAllResponse |
| GET | `/api/farms/{farmId}/alarm-events/stats` | 멤버 | `?days=7` (1~90, 위반 C001) | 200 AlarmStatsResponse |
| GET | `/api/farms/{farmId}/alarm-events/unacknowledged-count` | 멤버 | — | 200 AlarmUnacknowledgedCountResponse |

- **AlarmEventResponse** `{id, severity, sourceType, metricKey, ruleId?, scopeType?, scopeId?, message, status, occurredAt, acknowledgedAt?, acknowledgedBy?, resolvedAt?, resolvedBy?}` — `acknowledgedBy`/`resolvedBy`는 raw userId(이름 치환은 후속). `resolvedBy=null`+status=RESOLVED = 시스템 자동 해소.
- **AlarmEventDetailResponse** = 위 + `timeline: [{action, actorId?, note?, createdAt}]` (action=CREATED/ACKNOWLEDGED/RESOLVED/MEMO_ADDED)
- **AlarmMemoRequest** `{note}` — `@NotBlank`, 최대 1000자, 저장 시 trim. 상태 전이 없이 타임라인에만 추가.
- **AlarmStatsResponse** `{days, countBySeverity: {CRITICAL, WARNING}, avgAcknowledgeMinutes?}` — DB 집계(`GROUP BY severity` + `EXTRACT(EPOCH)/60`). 평균은 초 정밀도(구 구현의 분 단위 버림과 다름).
- ✅ **severity는 규칙이 결정한다**(2026-08-25, #118/PR #121) — `alarm_rules.severity`(CRITICAL/WARNING)가 그대로 이벤트에 실린다. `countBySeverity.CRITICAL`이 0이 아닐 수 있다.
- **접근 제어**: 조회는 `requireMember`, **처리(확인·해소·메모·일괄확인)는 `requireOperator`**(2026-08-25 #122) 선행 + `findByIdAndFarmId`로 조회(경로변수 불일치 IDOR 차단). actor는 `@AuthenticationPrincipal`에서만(요청 바디 미수용).
- **마이그레이션**: **V19** `alarm_events`·`alarm_event_logs`

## 4.14 알람 규칙 (2026-08-25 확정, 이슈 #118 — 미구현 도메인 정리 2)

- **모델**: 농장당 N개 규칙(`alarm_rules`, V20). §4.6의 `farm_env_thresholds`(농장당 1행·온습도 4컬럼)를 대체하되 **구 테이블·구 API는 유지**한다.
- **소스 3종**(지표 데이터 라우팅):
  | source | 데이터 | 스코프 | 비고 |
  |---|---|---|---|
  | `ENV_SNAPSHOT` | `env_snapshots`(§4.6) | **FARM 고정** | farmId 없는 전역 단일 스트림이라 하위 스코프 불가(DB CHECK로 강제) |
  | `SENSOR_READING` | `sensor_readings`(§4.11) | FARM/ZONE/RACK/LEVEL | `SensorMetric` 7종 |
  | `DEVICE_HEARTBEAT` | `devices` 상태 | FARM/ZONE/RACK/LEVEL | ⚠️ **현재 수동 토글 전용** — 후속 #119 참조 |
- **comparator**: `GT`·`LT`(→`threshold_value`) · `OUTSIDE_RANGE`(→`threshold_min`/`max`) · `ABSENT`(부재 판정, DEVICE_HEARTBEAT 전용 성격)
- **지속시간**: `duration_seconds` — **벽시계 기준**(최초 이탈 시각부터 경과). 구 "연속 2틱" 하드코딩의 일반화이며 **이관값은 60초**(연속 2틱의 실제 경과는 틱 간격 1회분이다 — 120으로 두면 기존 사용자 알람이 한 틱 늦어진다).
- **등급**: `severity`(CRITICAL/WARNING)를 규칙이 결정 → §4.13 이벤트에 그대로 실린다.
- **스코프 소멸 vs 빈 스코프**: 스코프 대상(zone/rack/level)이 soft delete되면 **열린 알람을 자동 해소하고 그 규칙을 건너뛴다**. 스코프는 살아있는데 관측이 없으면(장비 0대·측정값 없음) "관측 부재"로 **상태를 유지**한다(정상 복귀가 아니므로 해소하지 않는다).

| 메서드 | 경로 | 권한 | 응답 |
|---|---|---|---|
| GET | `/api/farms/{farmId}/alarm-rules` | 멤버 | 200 `List<AlarmRuleResponse>` |
| POST | `/api/farms/{farmId}/alarm-rules` | **ADMIN**(+데모 차단 A007) | 201 AlarmRuleResponse |
| GET | `/api/farms/{farmId}/alarm-rules/{id}` | 멤버 | 200 AlarmRuleResponse |
| PATCH | `/api/farms/{farmId}/alarm-rules/{id}` | **ADMIN**(+A007) | 200 AlarmRuleResponse |
| DELETE | `/api/farms/{farmId}/alarm-rules/{id}` | **ADMIN**(+A007) | 204 |

- **PATCH는 부분 수정** — 미전송 필드는 미변경. `source`/`metric`/`comparator`/`scopeType`/`scopeId`는 **수정 대상이 아니다**(요청 DTO에 없음 — 스코프 불변이라 cross-tenant 표면이 생기지 않는다).
- **상한**: 농장당 **50건**(ALR002). 판정은 farm 행 비관적 락 안에서 수행(check-then-act 레이스 차단). ⚠️ `PUT /env-thresholds`가 만드는 파생 규칙(최대 4개)도 이 카운트에 포함된다.
- **파생 규칙**: `PUT /env-thresholds`(§4.6)가 `threshold_id`로 묶인 규칙을 **제자리 upsert**한다(id 보존 → `metricKey` 불변 → 열린 알람 고아화 방지). 경계를 지우거나 설정을 끄면 `enabled=false`로 내리고 **열린 알람을 즉시 자동 해소**한다. `/alarm-rules`에서는 **읽기 전용**(수정·삭제 시 ALR004) — 두 API가 서로 덮어쓰지 못하게.
- **스코프 검증**: `scopeId`는 입력값 취급 — 생성 시 해당 zone/rack/level이 그 농장 소속인지 `findByIdAndFarmId`로 재검증하고, 아니면 **403이 아니라 404**(R001/R002/R003)로 존재 유추를 차단한다.
- **웹훅 쿨다운**: 농장×규칙별 30분. 규칙 **수정** 시 지속시간 카운터만 리셋하고 **쿨다운은 보존**한다(PATCH 반복으로 쿨다운을 우회해 외부 발송을 증폭하는 벡터 차단). 규칙 **삭제** 시에는 쿨다운까지 폐기한다. ⚠️ 부작용: 규칙을 껐다 켠 뒤 30분 내 재이탈하면 **이벤트는 생기지만 Discord 발송은 억제**된다.
- **웹훅 멘션 억제**: payload에 `allowed_mentions: {parse: []}` 고정 — 규칙 이름·장비 이름이 사용자 입력이라 `@everyone`이 해석되면 멘션 폭탄이 된다. 두 노티파이어(`EnvThreshold`·`Prescription`)가 **공유 payload 타입**을 쓴다(복사하면 한쪽만 고쳐진다).
- **ErrorCode**: `ALR001`(404 규칙 없음) · `ALR002`(409 상한 초과) · `ALR003`(400 검증 실패) · `ALR004`(409 파생 규칙 직접 수정 불가)
- **마이그레이션**: **V20** `alarm_rules` + 기존 `farm_env_thresholds` 이관 + 열린 `alarm_events`의 `metric_key` 재매핑 + 미대상 레거시 알람 자동 해소(감사 로그 동반)

## 4.15 CSV 내보내기 · 저장한 분석 (2026-08-25 확정, 이슈 #126 — 미구현 도메인 정리 4)

> **범위**: CSV + 저장한 분석만. PDF/XLSX 리포트 생성·예약은 새 의존성(iText/POI)+스케줄러가 필요해 후속 분리(사용자 결정).

### CSV 내보내기
| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/farms/{farmId}/readings/export.csv` | 멤버(VIEWER 이상) | `?metrics=&range=&scope=` (§4.11 `series`와 **동일**) | 200 `text/csv; charset=UTF-8` |

- **`series()`를 재사용한다** — 차트가 보여주는 **다운샘플 결과**를 그대로 CSV로 직렬화하며 별도 원본 쿼리가 없다. ⚠️ 원본 덤프로 바꾸면 §4.11이 경고한 "농장당 1틱 300행×90일"(수백만 행) 규모가 그대로 나와 별도의 무거운 상한 설계가 필요해진다.
- **행 수 상한 5760** = `MAX_SERIES_METRICS(4) × 24h 최대 버킷(1440)`. 현재 파라미터 조합으로는 구조적으로 초과 불가라 초과 응답은 **향후 버킷이 촘촘해질 때를 위한 방어선**이다.
- **UTF-8 BOM 포함** — unit 컬럼에 `°C`·`µmol/m²/s`가 실려 BOM이 없으면 Excel에서 깨진다(1차 소비자가 "다운로드 후 엑셀 더블클릭").
- 컬럼: `measuredAt, metric, unit, value`. 값이 전부 시스템 생성(타임스탬프·enum·`Double`)이라 **CSV 수식 인젝션 표면이 없다**(사용자 문자열이 셀에 실리지 않음 — 농장명·분석 이름 미포함).
- `Content-Disposition: attachment` + RFC 5987. 파일명 구성요소인 `scope`는 `ReadingScope.parse`가 `farm`·`{zone|rack|level}:{숫자}` 형식만 통과시켜 헤더 인젝션이 구조적으로 불가능하다.
- **권한이 `requireMember`인 이유**: VIEWER가 화면에서 이미 보는 것과 같은 쿼리·같은 스코프 검증 결과다. 파일 형식이라는 이유만으로 격상하면 §2 "VIEWER=조회전용" 정의와 어긋나고 실질 이득도 없다.

### 저장한 분석 (`saved_analyses`, V22)
| 메서드 | 경로 | 권한 | 응답 |
|---|---|---|---|
| GET | `/api/farms/{farmId}/saved-analyses` | 멤버 | 200 `List<SavedAnalysisResponse>` |
| POST | `/api/farms/{farmId}/saved-analyses` | **OPERATOR** | 201 SavedAnalysisResponse |
| PATCH | `/api/farms/{farmId}/saved-analyses/{id}` | **작성자 OR ADMIN** | 200 SavedAnalysisResponse (name만 수정) |
| DELETE | `/api/farms/{farmId}/saved-analyses/{id}` | **작성자 OR ADMIN** | 204 |

- **SavedAnalysisResponse** `{id, name, metrics[], range, scopeType, scopeId?, createdBy, createdAt, updatedAt}`
- `metrics`는 **JSONB**(`ChatMessage#sources`·`NutrientRecipe#calculationSnapshot` 관용구) — 최대 4개(`@Size(max=4)`)·중복 제거·`SensorMetric` 타입드 역직렬화라 임의 JSON 저장 경로가 없다
- **작성이 OPERATOR 이상인 이유**: VIEWER가 author가 되면 `author OR ADMIN` 삭제 규칙으로 **삭제 권한까지 획득**해 "조회전용"이 우회된다(#122에서 확립한 원칙)
- 농장당 **50건 상한** — `findByIdForUpdate` 농장 행 잠금 안에서 count(check-then-act 레이스 차단)
- `scopeId` 검증은 `AlarmScopeResolver.requireExists` 재사용 → **403이 아니라 404**(R001/R002/R003)
- ⚠️ **스코프 대상이 soft delete돼도 저장한 분석은 지우지 않는다** — 이 기능엔 "실행" 경로가 없다(재적용은 FE가 `GET /readings/series`를 직접 호출). #118의 알람 규칙과 달리 스코프 소멸이 조회 오류로 이어지지 않는다
- **ErrorCode**: `SA001`(404 분석 없음) · `SA002`(409 개수 상한) · `SA003`(413 내보내기 행 수 초과) · `SA004`(403 작성자·ADMIN 아님)

## 4.16 농약 참조정보 (2026-08-25 확정, 이슈 #128 — 미구현 도메인 정리 5)

> ⚠️ **이 데이터는 내부 시드 스텁이다.** 실 농진청 오픈API 키·스펙이 없어 자체 DB에 샘플을 시드하고 **연동 인터페이스(`PesticideReferenceProvider`)만 열어뒀다**(사용자 결정). 키를 확보하면 **provider 구현체만 교체**하면 되고 컨트롤러·DTO는 그대로다.

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/api/pesticide-references` | **인증만**(farm-scoped 아님) | `?cropType=&q=` | 200 `List<PesticideReferenceResponse>` |
| GET | `/api/pesticide-references/alerts` | **인증만** | `?cropType=` | 200 `List<PesticideAlertResponse>` |

- **PesticideReferenceResponse** `{cropType, pestName, registeredProductCount, preHarvestIntervalDays?, note, source, updatedAt}`
- **PesticideAlertResponse** `{cropType, message, severity, validFrom, validUntil, source}`
- `q`는 병해충명 **부분 검색**(생략 시 전체). `%`·`_`는 리터럴로 처리(`ESCAPE '\'`) — 이스케이프하지 않으면 `"50%"` 검색이 전체 매칭된다
- **결과 상한**: 참조 50건 · 경보 20건
- **경보는 유효기간 내만** 반환(`validFrom <= now <= validUntil`). "이번 주 발생 주의" 성격이라 기간 지난 경보가 계속 뜨면 안 된다
- 프리뷰 mock의 자유 텍스트(`"등록 약제 3종 · 수확전 7일"`)를 **구조화**했다 — 검색·필터가 요구사항이라 텍스트로는 처리 불가

### ⚠️ 출처 표기 규칙 (안전 — 임의 변경 금지)
이 데이터는 **농약 안전사용기간**이라, 사용자가 실제 기준으로 믿고 살포하면 **작물 피해·잔류농약 문제**로 이어진다. 프리뷰 mock 문구는 `"농촌진흥청 … 연동"`이지만 **실제 연동이 없으므로 그렇게 표기하지 않는다.**
- 참조 `source`: *"내부 시드 샘플 데이터입니다. 농촌진흥청과 실시간 연동되지 않으며 실제 등록 농약 정보와 다를 수 있습니다. 살포 전 반드시 농약안전정보시스템에서 정식 정보를 확인하세요."*
- 경보 `source`: *"내부 시드 샘플 경보입니다. 실제 농촌진흥청 예찰·발생정보와 연동되지 않으며 실제 발생 상황과 다를 수 있습니다. 방제 판단 전 반드시 농약안전정보시스템·지역 농업기술센터의 정식 예찰 정보를 확인하세요."*
- DB `NOT NULL` + 클래스 주석 + 테스트 3중 고정. Swagger에도 "(참고용 샘플 데이터)" 명시
- ⚠️ **경보에도 반드시 고지가 있어야 한다** — 경보 문구가 실제 관측처럼 읽혀 방제 판단을 오도할 수 있다(#128 리뷰 P2)

- **ErrorCode 없음** — 조회 전용이라 고유 실패 시나리오가 없다(`cropType` 검증은 공통 C001)
- **시드**: `init/PesticideReferenceSeeder`(Java initializer, idempotent). Flyway 정적 INSERT를 쓰지 않은 이유 — ①스텁이라 보강마다 마이그레이션이 쌓인다 ②경보 유효기간이 **시드 시점 기준 상대값**이어야 하는데 Flyway 고정 절대시각은 배포가 늦어질수록 처음부터 만료된 채로 심긴다
- **마이그레이션**: **V23** `pesticide_references`·`pesticide_alerts`

## 5. ErrorCode 체계

응답 형식: `{timestamp, code, message}` — GlobalExceptionHandler 일괄.

| 코드 | HTTP | 의미 |
|---|---|---|
| C001 | 400 | 요청 검증 실패(Bean Validation·본문 파싱 실패 포함) |
| C002 | 500 | 내부 서버 오류 |
| C003 | 404 | 존재하지 않는 경로 |
| C004 | 405 | 허용되지 않는 메서드 |
| C005 | 409 | 낙관적 락 충돌 — 다른 사용자가 먼저 처리(`@Version` 쓰는 모든 엔티티 공통) |
| A001 | 409 | 이메일 중복 |
| A002 | 401 | 이메일/비밀번호 불일치 |
| A003 | 401 | access 토큰 만료(refresh로 회복 가능) |
| A004 | 401 | 토큰 무효(변조·재사용·refresh 만료/미존재 포함 — 재로그인) |
| A005 | 403 | 접근 권한 없음 |
| A006 | 409 | ADMIN 농장 보유 — 탈퇴 불가(강등 또는 농장 삭제 후 재시도). ⚠️ **승격된 ADMIN도 해당** |
| A007 | 403 | 데모 계정에서 허용되지 않는 작업 |
| F001 | 404 | 농장 없음 |
| F002 | 403 | 농장 멤버 아님 |
| F003 | 403 | **관리자(ADMIN) 권한 필요** (2026-08-25 의미 재정의 — 구 "OWNER 권한 필요") |
| F004 | 400 | 초대코드 무효/만료 |
| F005 | 409 | 이미 농장 멤버 |
| F006 | 400 | **마지막 관리자는 강등·제거 불가** (2026-08-25 의미 확장 — 구 "OWNER는 탈퇴 불가"를 특수 사례로 흡수. 강등(`PATCH .../role`)에서도 발생) |
| F007 | 403 | 제어 권한(OPERATOR 이상) 필요 |
| F008 | 403 | **관리자 승인 대기 중**(PENDING) — 농장 접근 불가. F002(멤버 아님)와 구분 |
| F009 | 404 | 멤버십 없음(타 농장 소속 memberId 포함 — 존재 유추 차단) |
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
| L002 | 403 | 작업일지 수정/삭제 권한 없음(작성자 본인·삭제는 ADMIN 겸용) |
| W001 | 502 | 날씨예보 조회 실패(KMA 오류·캐시 없음) |
| N001 | 404 | 양액 레시피 없음 |
| N002 | 403 | 양액 레시피 수정/삭제 권한 없음(작성자 본인·삭제는 ADMIN 겸용) |
| N003 | 400 | 배합 불가(탱크 침전 위험·원수 보정 과다로 투입량 음수 등) |
| R001 | 404 | 존 없음(타 농장 소속 포함 — 존재 유추 차단) |
| R002 | 404 | 랙 없음(타 농장 소속 포함) |
| R003 | 404 | 층 없음(타 농장 소속 포함) |
| R004 | 409 | 랙 구조 변경 불가(층 축소 시 하위 장비 잔존·랙 코드 중복) |
| E001 | 404 | 장비 없음(타 농장 소속 포함) |
| E002 | 409 | 장비 시리얼 중복(농장 내) |
| CT001 | 404 | 제어 변경 항목 없음(타 농장·타 존 소속 포함) |
| CT002 | 409 | 통신 두절 장비는 조작할 수 없음 |
| CT003 | 409 | 현재 운전 모드에서 허용되지 않는 조작 |
| CT004 | 409 | 적용 대기 큐 상한 초과(존당 50건) |
| CT005 | 409 | 대기 큐가 변경됨 — 최신 큐로 재확인 후 재적용 |
| AL001 | 404 | 알람 이벤트 없음(타 농장 소속 포함) |
| AL002 | 409 | 현재 상태에서 처리할 수 없는 알람(잘못된 상태 전이) |
| ALR001 | 404 | 알람 규칙 없음(타 농장 소속 포함) |
| ALR002 | 409 | 알람 규칙 개수 상한 초과(농장당 50건 — 파생 규칙 포함) |
| ALR003 | 400 | 알람 규칙 검증 실패(comparator·임계값 조합 불일치, 공백 이름 등) |
| ALR004 | 409 | 파생 규칙은 직접 수정·삭제할 수 없음(`PUT /env-thresholds`로만) |
| SA001 | 404 | 저장한 분석 없음(타 농장 소속 포함) |
| SA002 | 409 | 저장한 분석 개수 상한 초과(농장당 50건) |
| SA003 | 413 | CSV 내보내기 행 수 상한 초과 |
| SA004 | 403 | 저장한 분석 수정·삭제 권한 없음(작성자 본인·ADMIN 겸용) |

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
