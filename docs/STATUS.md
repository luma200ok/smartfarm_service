# 📊 SmartFarm Service — 진행 현황 (STATUS)

> 마지막 갱신: **2026-08-23 (🏁 디자인 갭 **기반 2사이클 완료** — PR #94(랙·층+장비 레지스트리, V14)·PR #96(센서 측정값+시뮬레이터, V15~V17) 머지, 테스트 349→469. 계약은 두 사이클에서 **5회 보정**(P1급 결함 4건의 근인이 전부 계약). 다음 범위 협의 대기. 후속=#91·#92·#95. / 이전: 🧩 디자인 갭 사이클 1 완료 — PR #94 머지(랙·층+장비 레지스트리, V14, 테스트 419). PR #86 프리뷰·PR #93 a11y도 머지. **P1 2건 모두 근인이 계약 결함**이라 §4.10 2회 보정 후 §4.11도 선제 보강. 사이클 2(#90) 진행 중. 후속=#91·#92. / 이전: ✅ 터널 독립 스택 분리 완료 — PR #79·#82 + home-infra#4. 재현 테스트 통과(배포 실패시켜도 터널 무사). KMA env `:?required` 가드 적용. 후속=메모리 상한·530 감시. / 이전: 🚨 홈서버 전 서비스 장애 복구 — 터널 SPOF #75·KMA env 누락 #76. 아래 장애 이력 참조. / 이전: 2026-08-22 (🎯 데모 계정 라이브 — PR #58 BE(is_demo 시드·demo-login·A007 가드, opus 이중리뷰 P1 3건 픽스)·PR #59 FE(체험 버튼). deploy-home push 트리거(#39, 타 세션)로 자동 배포됨 → 수동 dispatch 불필요. 실사이트 검증: demo-login 200·데모 농장·삭제 403 A007. 후속=#51 데모 하드닝. / 이전: FE UX 개편 6건 머지 — PR #34 가드 레이스(#33)·#35 farms 분리(#32)·#37 다크 토글(#36)·#45 좌측 사이드바+농장 스위처(#42)·#46 농장 상세 탭(#43)·#48 프로필 메뉴(#47). 후속=#44 중복 조회 정리. deploy-home 배포. ⚠️ 구 deploy.yml(OCI push 배포)이 머지마다 arm1에 재배포됨 — 정지 상태와 충돌, 트리거 제거 필요. 이전 갱신: 🏁 OCI 이전 전체 종료 — 전 서비스 컷오버+마무리 완료, OCI 정지·관찰 중(해지만 남음). 상세=`_local/oci-migration-plan.md`)**
> 새 세션은 이 문서 + [api-contract.md](api-contract.md) 로 현황 파악.

## 🏠 홈서버 이전 (이슈 #27 — OCI 폐기 결정)
- **배경**: OCI Free Tier 축소로 arm1·arm2 전면 폐기 → 홈서버(x86_64, 32GB, Ubuntu 26.04, `192.168.0.6`) 이전. DNS 전환까지 OCI 배포 병행.
- **PR-1 ✅ (PR #28)**: backend/frontend Dockerfile + `deploy/home/`(compose·nginx·cloudflared·.env.example·세팅 README). 기존 파일 무수정. 리뷰 P1 1(업로드 UID 권한)→픽스, P2 4 반영.
- **PR-2 ✅ (PR #29)**: deploy-home.yml(dispatch 전용) + smartfarm-deploy.sh(sudo 헬퍼). opus 리뷰 2종×2라운드로 보안 P1 3건 해소 — 러너=전용 runner 계정(도커 그룹 X), sudoers 인자 고정, 배포 입력=root 소유 /opt/smartfarm/repo(origin/main 직접 체크아웃, 워크스페이스 미사용). 잔여 보안 항목은 후속 이슈 분리.
- **PR #30 ✅**: 첫 배포의 frontend unhealthy 픽스 — Next standalone HOSTNAME 바인딩 함정 + alpine localhost=::1. **홈서버 첫 배포 성공: backend·frontend healthy, 내부 스모크 200.**
- **PR-3 (다음)**: Public Hostname(farm-home) 등록 후 외부 e2e 스모크 + 컷오버 체크리스트 / **PR-4**: 컷오버(DNS 전환+최종 데이터 동기, 이슈 #27 닫음).
- 홈서버 DB: 공용 스택 `~/srv/db/compose.yml`(**pgvector/pg16**·mysql8·redis7), 리허설 복원 검증 완료. 이전 순서·백업 현황은 메모리 `oci-migration-progress` 참조.

## 🚨 장애 이력

### ✅ 2026-08-23 구조 개선 완료 — 재발 경로 차단

장애 복구에 그치지 않고 **원인 구조 자체**를 바꿨다. 터널을 앱 스택에서 분리했다.

| PR | 내용 |
|---|---|
| #79 | `nginx`를 `shared-net`에 조인 — 터널 분리의 선행 조건(G1이 잡아낸 502 위험) |
| luma200ok/home-infra#4 | 터널 독립 스택(compose + runbook). 신설 레포 |
| #82 | `cloudflared` 정의 제거 + KMA env `:?required` 가드 + 거짓 주석 정정 |

**재현 테스트 통과** — 운영 `.env`에서 `KMA_SERVICE_KEY`를 제거하고 배포를 실행(8/23과 동일 트리거):

```
error while interpolating services.backend.environment.KMA_SERVICE_KEY:
  required variable KMA_SERVICE_KEY is missing a value
```

배포는 **compose 파싱 단계에서 멈췄고**(컨테이너 생성 전), `tunnel-cloudflared-1`은 건드려지지도 않았으며
10개 도메인 전부 정상이었다. 8/23에는 같은 트리거로 전 도메인 530이 40분간 지속됐다.

**두 겹의 방어가 각각 독립적으로 작동한다:**
1. `:?required` 가드 — 컨테이너를 하나도 건드리기 전에 배포를 멈춘다
2. 스택 분리 — 배포가 진행돼 backend가 죽어도 터널은 다른 스택이라 무관하다

⚠️ **`deploy/home/compose.yml`에 `cloudflared`를 다시 추가하지 말 것.** 앱 배포 실패가 전 서비스를 내리는 구조가 되살아난다.
⚠️ **`nginx`의 `shared-net` 조인을 지우지 말 것.** 터널이 nginx에 닿는 유일한 경로다 — 지우면 `farm`만 502가 된다(찾기 어려운 형태).

후속: home-infra#2(메모리 상한 — 24개 컨테이너 전부 무제한), home-infra#3(530 감지 — 40분간 아무도 몰랐던 문제는 아직 그대로)

### 2026-08-23 04:00 — 홈서버 전 서비스 다운 (약 40분) → 복구 완료
- **현상**: `luma200ok.com` 포함 **홈서버 전 도메인 530**(Cloudflare Error 1033, tunnel down). hajacheck·community·mes 등 smartfarm 무관 서비스까지 전부 다운. 이어 `farm.luma200ok.com` 502.
- **원인 2건** (모두 03:28 #56 배포가 유발):
  1. `cloudflared`가 **smartfarm-home compose에 포함**되어 배포 시 recreate → backend 기동 실패로 up 중단 → 터널이 `Created` 상태로 start 안 됨. `restart: unless-stopped`는 한 번도 start 안 된 컨테이너엔 무효라 자동 복구 불가 → **이슈 #75**
  2. #56이 요구하는 `KMA_SERVICE_KEY`/`KMA_GRID_NX`/`KMA_GRID_NY`가 **운영 `.env` 미반영**(`.env.example`에만 추가) → `KmaProperties` fail-fast로 backend 크래시 루프 → **이슈 #76**
- **복구**: 터널 컨테이너 기동 → 전 도메인 200 / 운영 `.env`에 KMA 3키 주입(서비스키=smartfarm-ai 스택 동일 키 재사용, 격자 60·127) → 재배포 후 backend Healthy, `farm` 200·`/api/health` ok
- **교훈**: `.env.example` 갱신 ≠ 운영 반영. **필수 env 추가는 코드 머지와 서버 반영이 한 세트.** 터널은 앱 스택과 분리해야 한다.

## 개요
smartfarm_ai(솔로 프로젝트, ML/DL/LLM)를 **멀티테넌시 3-tier 서비스**로 확장.
Spring Boot backend + Next.js frontend 신규(이 레포), ai-server는 기존 `smartfarm_ai` 레포 FastAPI 재사용(1차 무변경).

## 인프라 (arm1 올인, 전부 네이티브 systemd — 도커 미사용)
| 항목 | 값 | 상태 |
|---|---|---|
| 서버 | oci-arm1 (3코어/16GB, 158.179.169.146) | 가동 중 |
| 도메인 | `farm.luma200ok.com` (Cloudflare Proxied) | ✅ HTTPS 라이브(certbot, 외부 스모크 통과) |
| backend | Spring Boot 3.x·Java 21, 127.0.0.1:8085, `-Xmx512m`, systemd | ✅ 가동 (V1~V4 적용, /api/health ok) |
| frontend | Next.js standalone, 127.0.0.1:3000, systemd (**서버 빌드 금지** — GH Actions 빌드→산출물 배포) | ✅ 가동 (Node 22, WorkingDirectory 미사용 — SELinux, PR #16) |
| DB | **네이티브 PostgreSQL 16**에 `smartfarm_service` DB 신설 (hajacheck 도커 PG와 별개) | ✅ 생성·마이그레이션 적용 |
| ai-server | 기존 `smartfarm-api.service` (FastAPI :8000, 외부 비노출) | ✅ 가동 중 |
| LLM | 로컬 Ollama (qwen2.5:7b + exaone3.5:2.4b + bge-m3) | ✅ 가동 중 |
| 시크릿 | `/etc/app-secrets/smartfarm-service.env` (root:600, prod 프로필 포함 7키) | ✅ 생성 |
| CI/CD | GitHub Actions — PR CI + main 배포(jar 백업·심링크 스왑·헬스체크) | ✅ **green 실측**(run 32269609655) |

⚠️ 용량 전제: LLM 로드 피크 12~14GB/16GB. 처방은 backend 단일 워커로 직렬화(contract §3). Next 빌드는 절대 서버에서 하지 않음.
⚠️ **backend는 단일 인스턴스 전제**(처방 워커 복구·픽업이 인스턴스 구분 없음 — 스케일아웃 시 owner/heartbeat 필요, #9 참조). 스케일아웃 금지.

## 마지막 머지 PR
- **PR #58** (이슈 #49) — BE 데모 계정: is_demo 시드(선점 fail-fast)·demo-login·A007 가드 8곳·nginx limit_req·데모 refresh 전역 revoke 생략. opus 이중 리뷰 P1 3→0, 테스트 193건. 후속 #51
- **PR #59** (이슈 #50) — FE 데모 체험 버튼(demoLogin API·A007 메시지). APPROVE
- **PR #48** (이슈 #47) — FE 우측 상단 프로필 메뉴(getMe 아바타+드롭다운, 로그아웃 일원화·LogoutButton 삭제). P1 1(이중 마운트)→픽스, P2 3 픽스, 재검토 PASS
- **PR #46** (이슈 #43) — FE 농장 상세 탭(개요·진단·처방·멤버, farms/[farmId]/layout). FarmDetail→Overview/Members 분리, 기존 URL 유지. P1 0 APPROVE
- **PR #45** (이슈 #42) — FE 좌측 사이드바(내비+내 농장 스위처, farmsBus 갱신). P2 2건 픽스, 후속 #44
- **PR #41** (이슈 #38) — FE 전역 헤더 통일(usePathname 활성 내비, 홈 포함) + 테마 토글 필 스위치화. P1 0 APPROVE
- **PR #37** (이슈 #36) — FE 라이트/다크 토글. Tailwind 4 @custom-variant .dark 전환 + no-flash 스크립트(farmTheme) + ThemeToggle 3곳. P1 0·P2 1(인증화면 겹침) 픽스
- **PR #35** (이슈 #32) — FE /farms 목록/생성 분리 + 공용 Modal(포커스 트랩·복귀·스크롤 잠금). reviewer P1 2라운드(트랩 부재→오픈 직후 Shift+Tab 이탈) 픽스 후 재검증 PASS
- **PR #34** (이슈 #33) — FE 인증 가드 hydration 레이스 픽스(useSyncExternalStore 서버 스냅샷 false 고정 → 마운트 후 직접 검사) + 로그인 역가드. P1 0·P2 1 픽스
- **PR #14** (이슈 #4) — BE 처방 job. opus 2종 P1 3→0(한글 스키마 fail-open·접수 무제한), 테스트 120건
- **PR #13** (이슈 #3) — BE 진단 프록시+이력. P1 1(트랜잭션 내 외부 호출) 픽스, 테스트 85건
- **PR #12** (이슈 #2) — BE Farm 멀티테넌시. opus 2종 P1 1→0, IDOR 전수 통과, 테스트 70건
- **PR #11** (이슈 #6) — FE 농장·진단·처방 화면. P2 2 픽스 후 머지
- **PR #10** (이슈 #1) — BE 스캐폴드+JWT 인증. code-reviewer(opus) P1 2 → 2라운드 픽스 → 잔존 0 / security-reviewer(opus) P1 0. 테스트 27건(Testcontainers PG16)
- PR #8 (이슈 #5) — FE 스캐폴드+인증 화면

## 다음 작업
### P0 (Phase 1 — 백엔드 코어)
- [x] #1 [BE] 스캐폴드 + Auth(JWT) + 예외/ErrorCode 뼈대 (PR #10)
- [x] #2 [BE] Farm 멀티테넌시 (PR #12)
### P1 (Phase 1 — 도메인 + 프론트)
- [x] #3 [BE] 진단 프록시 + 이력 (PR #13)
- [x] #4 [BE] 처방 비동기 job (PR #14)
- [x] #5 [FE] 스캐폴드 + 인증 화면 (PR #8)
- [x] #6 [FE] 농장·진단·처방 화면 (PR #11)
### P2 (Phase 2 — 배포)
- [x] #7 [INFRA] arm1 배포 완주 (PR #15·#16, 파이프라인 green·HTTPS·스모크 통과)

## 🗺️ 다음 플랜 (✅ PRD v1.0 확정 — docs/prd/PRD_smartfarm_service.md §2.3·§11 기준, 2026-08-20)

### P0 — 사이클 1 (✅ 완료 2026-08-20)
- [x] FE 마감 (PR #17)
- [x] BE 하드닝 (PR #18) — ArchUnit이 기존 잠복 불일치 1건 즉시 검출·픽스

### P1 — Phase 3 (2026-08-20 결정: 원칙 해제·전부 착수)
- [x] smartfarm_ai#67 — 환경 조회 API·fallback·caller_ref (머지·배포, 환경 API 라이브)
- [x] #19 회원 탈퇴 (PR #23 — 4중 봉쇄·즉시 익명화·재인증)
- [x] #20 이미지 원본 저장 (PR #24 — 화이트리스트·매직바이트·인가 스트리밍)
- [x] #21 웹훅 알림 (PR #25 — SSRF 차단·토큰 로그 유출 P1 픽스)
- [x] #22 환경 대시보드 (PR #26 — 전역 60s 캐시·stale 폴백·FE 위젯, 라이브 스모크 통과): smartfarm_ai '오늘 운영'(KMA 외기·제어 후 내부값·장치 상태)을 서비스 대시보드에 노출 — ai-server 조회 엔드포인트 추가 필요 = **"1차 ai-server 무변경" 원칙 첫 해제 지점**, 범위 협의 필수

### P1.5 — 다함 벤치마킹 (2026-08-22 등재 — 다함팜 플랫폼 DFX-APP·통합관제 DFX-SCADA 갭 분석 결과. 계약=api-contract §4.6)
- **사이클 1**: [x] #52 [BE] 환경 시계열 적재+임계치 웹훅 (**PR #61 머지 2026-08-22** — V9·V10, 60s 폴러+연속2틱·쿨다운30분 알림, 리뷰 P1 0·P2 1(데모 임계치 차단→§4.5 갱신)·P3 2 전량 본 PR 픽스, 테스트 229) · [x] #53 [FE] 시계열 차트+임계치 설정 UI (**PR #60 머지 2026-08-23** — recharts 기간탭·외기/내부 시리즈·다크 대응·임계치 폼(OWNER), 리뷰 P1 0·P2 2+P3 1 전량 픽스) → **사이클 1 완료**
- **인프라**: [x] #62 구 deploy.yml OCI push 트리거 제거(PR #63) · [x] #67 backend KMA env 전달+cloudflared shared-net(PR #68) · ai#88 streamlit shared-net(ai PR #89)
- **후속 이슈**: #70 [SEC] 농장 개수 상한·/api 일반 레이트리밋·가입 남용 방어(#54 P1의 챗 범위 밖 근본 원인) · #51 데모 하드닝(데모 공유 농장이 사실상 전역 챗 쿼터가 되는 UX 트레이드오프 포함)
- **핫픽스**: [x] #73 KMA env 기본값 누락으로 backend 기동 실패(사이트 502) — **PR #74 머지**: `${KMA_*:기본값}` 부여 + `ApplicationYmlPlaceholderTest`(기본값 없는 placeholder는 필수 시크릿만 허용, 핫픽스 역적용 시 FAILED 실측). 기존 테스트가 못 잡은 이유=application-test.yml이 kma 값을 채워 미설정 경로 미실행
- **핫픽스 2건 추가 (2026-08-23 오후, 라이브 검증 중 발견)**:
  - [x] #80 외부 호출 타임아웃이 `CancellationException`으로 새어 500 (**PR #81**) — JDK HttpClient가 읽기 타임아웃을 `RestClientException`이 아닌 `CancellationException`으로 표면화. AI 클라이언트 4종이 못 잡아 챗은 CH001 대신 500, **환경 대시보드는 stale 폴백 없이 500**이 될 잠재 경로였음. 챗 타임아웃 30s→120s(콜드스타트 초과), **nginx `proxy_read_timeout` 미설정=기본 60s** 도 함께 상향(125s), KMA DEBUG 로그의 서비스키 유출 마스킹
  - [x] #84 KMA base-url 오타(`VilageFcstInfoService2.0` → `_2.0`) (**PR #85**) — 언더스코어 누락으로 **키와 무관하게 존재하지 않는 엔드포인트 호출**. 공개 API 더미키 대조로 확정. 회귀 가드 테스트 동반(오타 시 FAILED 실측)
- **🧪 라이브 실동작 검증 완료 (2026-08-23 08:2x)**: environment today/history/forecast·env-thresholds·logs·chat·nutrient-recipes/presets **전부 200**, 프론트 4개 라우트 200. 챗은 실제 한국어 답변 28.9초(웜), 예보는 실제 KMA 데이터 2.2초
- **⚠️ 배포 함정 발견**: `deploy/home/nginx.conf`처럼 **바인드 마운트되는 설정 파일은 배포해도 컨테이너가 재생성되지 않아 조용히 무시된다**(이번에 nginx 수동 재시작으로 확인). 헬퍼가 nginx 리로드하도록 보완 필요 — 후속
- **🎨 디자인 개편 (2026-08-23 착수)**: [x] #83 디자인 핸드오프 10화면 `/design-preview` 프리뷰 (**PR #86 머지** — PC 6종+모바일 4종, 목업 데이터, 다크 팔레트 신설. 리뷰 P1 0·P2 7→6픽스·1후속. G6 PASS. 함정=Tailwind 4가 미디어쿼리를 폭이 아니라 **테마 선언 순서**로 출력 → 커스텀 브레이크포인트가 기본 `sm~2xl`을 덮음, 임의 변형 `min-[1440px]:`로 통일해 회피)
- **🧩 디자인 갭 대응 (프리뷰 → 백엔드, 2026-08-23 착수)**: 프리뷰가 전제하는 백엔드는 현 규모의 약 2배. 갭 조사 결과 **구조적 갭 4건**(① `env_snapshots`에 farmId 없음=전 농장 공용 단일 스트림 ② 측정 항목이 온습도 2종뿐, 프리뷰는 7종 ③ Farm 아래 랙·층 개념 없음 ④ `FarmRole` 2단계 vs 프리뷰 4단계) + **신설 도메인 8건**(랙·층, 장비 레지스트리, 장비 제어, 알람 이벤트, 알람 규칙 확장, 리포트/CSV, 스케줄·자동화, 농약 참조정보)
  - **사용자 결정(2026-08-23)**: 실기기 부재 영역은 **가상 장비 시뮬레이터**로 구현(기존 "원격제어·EC/pH 실시간 제외" 결정을 **시뮬레이션 전제로 한정 해제**, 응답에 `simulated: true` 명시) · 범위는 **기반 2사이클 먼저** 후 재판단
  - **계약 확정**: api-contract **§4.10**(랙·층 계층 + 장비 레지스트리, ErrorCode R001~R004·E001~E002, V14) · **§4.11**(센서 측정값 + 시뮬레이터, V15). ⚠️ **`env_snapshots`는 무변경 공존** — farmId 소급 부여는 데이터 의미 왜곡 + 라이브 대시보드(#22)·차트(#53) 파손
  - **사이클 1**: [x] #89 [BE] 랙·층 구조 + 장비/센서 레지스트리 (**PR #94 머지 2026-08-23** — 엔티티 4종·V14·엔드포인트 12개·ErrorCode R001~R004·E001~E002, 테스트 349→419). **P1 2건 모두 근인이 계약 결함**: ① 랙·존 삭제가 Device 고아 생성(`levelCount` 축소엔 R004가 있는데 통삭제엔 없어 우회로) ② FK 정합성을 **쌍 2개**로만 적어 `rackId` 생략 조합이 검증을 통과(전이 규칙 ③ 누락). 계약 2회 보정(`65ff438`, `3c4f37b`). ⚠️ **등급 충돌 처리**: ①에서 code-reviewer P1 / security-reviewer P2로 갈렸고 security 근거가 "계약이 장비를 언급 안 하니 구현은 준수"였는데 **그 계약을 쓴 게 메타** → 계약 결함은 격하 근거가 아니므로 **상향 확정**. 안전 확인: cross-tenant IDOR 12개 엔드포인트 전부 방어(body FK·중첩경로·404 통일), `jakarta` vs `org.hibernate` 동명 `ConstraintViolationException` 혼동 없음(후자를 잡았으면 unique race가 조용히 400으로 뭉개졌을 것)
  - **사이클 2**: [x] #90 [BE] 센서 측정값 + 가상 장비 시뮬레이터 (**PR #96 머지 2026-08-23** — V15 `sensor_readings`·V16 `device_metrics`·V17 인덱스/unique, 조회 3종, 테스트 419→469). **P1 2건(security-reviewer 단독 발견, code-reviewer는 놓침)**: ① `findLatestPerLevel`의 CTE만 `farm_id`/`metric`으로 좁히고 **값을 만드는 바깥 SELECT에는 술어가 없어** 랙 도면이 전 지표 평균을 표시(온습도 센서 1대만으로 `(22.4+64.8)/2=43.6 "°C" → CRITICAL`, 정상 운영에서 100% 발생) — 조회 테스트가 단일 지표만 적재해 못 잡음 ② **purge 처리량이 유입의 1/21**(20,000 vs 172,800~432,000행/일)이라 보존 90일 성립 불가·디스크 무한 증가 — **근인은 메타 handoff의 "purge 패턴 그대로"**(유입량이 120배 다른데 상수까지 복사됨). **픽스가 만든 새 실패 모드 2건**: 농장 루프 예외 격리 부재(V17 unique 위반 시 뒤 순서 농장 전부 유실 — 이 PR에서 픽스) · tail 테넌트 영구 기아(→#95). **죽은 테스트 1건 발견**: `anySatisfy` 안의 `if`로 실패할 수 없는 assertion(값이 336.2로 오염돼도 초록). 계약은 사이클 2에서만 **3회 보정**(Device.metrics·부모 FK 자동 채움·purge 처리량/전역 상한/신선도 상한)
  - **🏁 기반 2사이클 완료** — 사용자가 정한 "기반 먼저 후 재판단" 지점 도달. 다음 범위 협의 필요. **스폰 전 §4.11 선제 보강**(`f2321c9`) — 사이클 1 회고를 적용해 같은 계열 구멍 5개 차단: 비정규화 위치의 **진실 소스=적재 시점 스냅샷**(소급 갱신·join 유도 금지) · 부모 soft delete된 구조를 참조하는 readings는 정상 상태(조회는 활성 구조만 렌더, 이력은 보존) · **`source` 컬럼**(시뮬레이터 끄고 실기기 붙으면 한 테이블에 섞여 사후 구분 불가) · 스코프 query 파라미터도 소속 검증 404 · 집계 이중성(device간 평균→시간 평균)·응답 상한
  - **후속 판단 대기**: 알람 이벤트(사이클 3) · 제어 도메인(사이클 4) · 리포트/권한 4단계/농약(사이클 5+) · FE의 `mock.ts` → 실 API 교체
  - **후속 이슈**: **#95** 시뮬레이터 전역 상한의 농장 배분 정책 부재(tail 테넌트 영구 기아 — 농장 순회가 device id 기준 고정이라 예산 소진 뒤 농장은 영원히 0행, 약 16개 농장이 한계. 현 규모 미발현) · **#91** farm-scoped 목록 페이지네이션 + 리소스 생성 상한 + **삭제 R004 가드의 TOCTOU**(부모 행 락 부재 — 삭제와 장비 생성이 겹치면 고아 발생, P2) + 인가 테스트 잔여 + ArchUnit 규칙④(인가 호출 정적 가드 — 규칙①은 `FarmAccessGuard` **의존 여부**만 봐서 `requireOwner`→`requireMember` 강등을 못 잡음) · **#92** 랙 배치도 셀·할일 카드 a11y(⚠️ 랙 셀은 최소 14px까지 줄어 마커 재사용 불가할 수 있음)
- **♿ 프리뷰 접근성**: [x] #88 지표 이상값이 색으로만 구분 (**PR #93 머지 2026-08-23** — `AlertMarker` 공통 컴포넌트, `aria-hidden` 글리프로 sr-only와 중복 낭독 방지. 구현 중 **PC 홈에는 sr-only가 아예 없어 스크린리더도 색만 보고 있던 것** 추가 발견·수정)
- **⚠️ 사용자 조치 대기**: ① `KMA_SERVICE_KEY`를 smartfarm-ai 스택 .env에서 service 스택 .env로 복사(값 출력 없이) + 재배포 → 예보 라이브 ② Cloudflare Zero Trust Public Hostname `smartfarm-ai.luma200ok.com` → `http://smartfarm-ai-streamlit:8501` 등록 → 502 해소(⚠️Streamlit 무인증 — CF Access 권고)
- **사이클 2·3 계약 확정(2026-08-23)**: §4.7 챗봇(ai-server 신규 POST /api/chat — 무변경 원칙 3번째 해제, 이력 정책=태깅 확정)·§4.8 작업일지(V11)·KMA 예보. smartfarm_ai#84 등재. ⚠️ V12는 #54(챗) 예약
- **사이클 2**: [x] **smartfarm_ai#84 챗 라우트 `POST /api/chat` 머지·홈서버 배포 완료**(PR #85 — RAG·세마포어 재사용, 챗 전용 상한 1로 진단/처방 기아 차단, JSONB 라운드트립 실측. 부수: ai 레포 OCI 배포 트리거 정리 #86/PR #87) · [x] #54 [BE] 챗 프록시+이력 (**PR #71 머지 2026-08-23** — V12 chat_messages, CH001/CH002. **보안 P1 수용·픽스**: 농장 곱하기 우회 → 3중 방어(농장+사용자 레이트리밋+전역 Semaphore(2) 즉시 429). ArchUnit 수동 목록 폐기(2회 재발 구조적 차단). 테스트 290) · [x] #55 [FE] 챗봇 UI (**PR #72 머지** — answer/sources를 JSX 텍스트 노드로만 렌더, 메타 grep+리뷰어 전수 이중 검증) → **사이클 2 완료**
- **사이클 3 (퀵윈)**: [x] #57 [FE] 일지·예보·VPD 위젯 (**PR #69 머지** — VPD 검산 문헌값 일치 확인) · [x] #56 [BE] 작업일지 CRUD+KMA 단기예보 (**PR #66 머지 2026-08-23** — V11 farm_logs·L001/L002/W001. 메인 검수에서 실버그 2건 발견·수정: KMA 인증키 `+` 미인코딩(실키 인증 실패), 예보 타임아웃 테스트 무한정지. 리뷰 P1 0·P2 1·P3 3 전량 픽스, 테스트 264)
  - ⚠️ **`KMA_SERVICE_KEY` 홈서버 env 미등록** — 등록 전까지 예보 API만 W001(다른 기능 무영향)
- **사이클 4 (양액)**: [x] #64 [BE] 배합 계산+레시피 CRUD (**PR #77 머지 2026-08-23** — V13, 침전 검증 로직 강제. **프리셋 출처=Kroggel & Kubota 2018 OSU HYG-1437, 메타가 원문 대조 검증** / 리뷰 P1(K2SO4 미기재)은 **계약 초판이 화학적으로 틀린 것으로 판정→계약 정정**(KNO3로 K 보충 시 N 초과=직접 피해, K2SO4의 S는 출처 허용범위 내) / P2: 이온밸런스 임계 10%→30%(정상 프리셋 4단계가 전부 경고=alarm fatigue였음). 테스트 335) · [x] #65 [FE] 계산기 UI+레시피 관리 (**PR #78 머지 2026-08-23** — 안전 고지·출처·편차 수치 상시 노출, FE 계산 재구현 없음(서버 표시만). 독립 리뷰 P1 0·P2 1(계산 후 입력 변경 시 stale 배합표+활성 저장 버튼)·P3 1 픽스) → **🏁 다함 벤치마킹 5개 사이클 전부 완료**
  - ⚠️ 절차 이탈 기록: #65 구현 서브가 자체 리뷰어 호출·픽스 서브 스폰(룰=메타 주도) → 자체 리뷰 불인정, 메타가 독립 리뷰 재실행
- 제외 결정: 원격제어·타사장비·에너지·**양액기 실제 제어/EC·pH 실시간**(실기기·센서 부재 — 계산과 레시피만 채택)

### P2 — 운영 안정화
- [x] PG 백업 cron — 매일 04:10·14일 보관, 1회 실행+pg_restore 목록 검증 완료(8테이블) (2026-08-20)
- [x] certbot 자동갱신 — /etc/cron.d/certbot-renew(3·15시, 전체 인증서 대상) farm 자동 포함 확인
- [ ] processing_started_at(V5)·@Lob 제거 등 #9 구조 개선 잔여
- [ ] README 아키텍처 다이어그램·라이브 링크(포트폴리오 정리)

## 알려진 이슈 / 결정 기록
| 항목 | 내용 |
|---|---|
| DB 엔진 | PG16 확정(2026-08-19). arm1 mysqld는 6월 이전 잔재(구 community·mes·planner·smartfarm 스키마) — 신규 서비스와 무관, 추후 정리 후보 |
| 멀티테넌시 | Farm=테넌트, path `{farmId}` 입력값 취급·매 요청 멤버십 재검증 (contract §2) |
| 이미지 저장 | 1차 미저장(진단 결과만 이력화) |
| ai-server 질문 이력 | PRD §11-1 — smartfarm_ai#66 으로 등재(비활성/익명화/태깅 택일 결정 대기). 연동 개선 umbrella 포함 |
| FE P004 반영 | ✅ PR #17에서 해소 |
| cropType | 1차 TOMATO 전용(ai-server 모델 제약), enum 확장 대비 |
| ⚠️ deploy.yml | 구 OCI(arm1) push 배포가 아직 활성 — main 머지마다 arm1 frontend 재기동(2026-08-21 PR #34 머지 시 실측, run 32461101202 success). OCI 정지 상태와 충돌 → push 트리거 제거/워크플로우 폐기 필요(사용자 결정 대기) |
