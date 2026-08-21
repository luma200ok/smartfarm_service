# 📊 SmartFarm Service — 진행 현황 (STATUS)

> 마지막 갱신: **2026-08-21 (🏁 OCI 이전 전체 종료 — 전 서비스 컷오버+마무리(pension 스토리지 로컬화·외부 백업 cron·골든 덤프) 완료, OCI 전 서비스 정지·관찰 중(해지만 남음). 루트/www=포트폴리오·smartfarm-ai=Streamlit 데모·stats=ghstats 터널 공개. smartfarm_ai#73 UI 톤다운 머지(PR #74). 상세=`_local/oci-migration-plan.md`)**
> 새 세션은 이 문서 + [api-contract.md](api-contract.md) 로 현황 파악.

## 🏠 홈서버 이전 (이슈 #27 — OCI 폐기 결정)
- **배경**: OCI Free Tier 축소로 arm1·arm2 전면 폐기 → 홈서버(x86_64, 32GB, Ubuntu 26.04, `192.168.0.6`) 이전. DNS 전환까지 OCI 배포 병행.
- **PR-1 ✅ (PR #28)**: backend/frontend Dockerfile + `deploy/home/`(compose·nginx·cloudflared·.env.example·세팅 README). 기존 파일 무수정. 리뷰 P1 1(업로드 UID 권한)→픽스, P2 4 반영.
- **PR-2 ✅ (PR #29)**: deploy-home.yml(dispatch 전용) + smartfarm-deploy.sh(sudo 헬퍼). opus 리뷰 2종×2라운드로 보안 P1 3건 해소 — 러너=전용 runner 계정(도커 그룹 X), sudoers 인자 고정, 배포 입력=root 소유 /opt/smartfarm/repo(origin/main 직접 체크아웃, 워크스페이스 미사용). 잔여 보안 항목은 후속 이슈 분리.
- **PR #30 ✅**: 첫 배포의 frontend unhealthy 픽스 — Next standalone HOSTNAME 바인딩 함정 + alpine localhost=::1. **홈서버 첫 배포 성공: backend·frontend healthy, 내부 스모크 200.**
- **PR-3 (다음)**: Public Hostname(farm-home) 등록 후 외부 e2e 스모크 + 컷오버 체크리스트 / **PR-4**: 컷오버(DNS 전환+최종 데이터 동기, 이슈 #27 닫음).
- 홈서버 DB: 공용 스택 `~/srv/db/compose.yml`(**pgvector/pg16**·mysql8·redis7), 리허설 복원 검증 완료. 이전 순서·백업 현황은 메모리 `oci-migration-progress` 참조.

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
