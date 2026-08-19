# 📊 SmartFarm Service — 진행 현황 (STATUS)

> 마지막 갱신: **2026-08-19 (PR #10 머지 — BE #1 스캐폴드+JWT 인증, Critical 2라운드·P1 잔존 0·테스트 27건. 후속 #9 umbrella 생성)**
> 새 세션은 이 문서 + [api-contract.md](api-contract.md) 로 현황 파악.

## 개요
smartfarm_ai(솔로 프로젝트, ML/DL/LLM)를 **멀티테넌시 3-tier 서비스**로 확장.
Spring Boot backend + Next.js frontend 신규(이 레포), ai-server는 기존 `smartfarm_ai` 레포 FastAPI 재사용(1차 무변경).

## 인프라 (arm1 올인, 전부 네이티브 systemd — 도커 미사용)
| 항목 | 값 | 상태 |
|---|---|---|
| 서버 | oci-arm1 (3코어/16GB, 158.179.169.146) | 가동 중 |
| 도메인 | `farm.luma200ok.com` (Cloudflare) | ⬜ DNS·certbot 미설정 |
| backend | Spring Boot 3.x·Java 21, 127.0.0.1:8085, `-Xmx512m`, systemd | ⬜ 미구현 |
| frontend | Next.js standalone, 127.0.0.1:3000, systemd (**서버 빌드 금지** — GH Actions 빌드→산출물 배포) | ⬜ 미구현 (arm1에 Node 설치 필요) |
| DB | **네이티브 PostgreSQL 16**에 `smartfarm_service` DB 신설 (hajacheck 도커 PG와 별개) | ⬜ DB 미생성 |
| ai-server | 기존 `smartfarm-api.service` (FastAPI :8000, 외부 비노출) | ✅ 가동 중 |
| LLM | 로컬 Ollama (qwen2.5:7b + exaone3.5:2.4b + bge-m3) | ✅ 가동 중 |
| 시크릿 | `/etc/app-secrets/smartfarm-service.env` (root:600) | ⬜ 미생성 |
| CI/CD | GitHub Actions — PR CI(빌드/테스트) + main 머지 시 배포 | ⬜ 미구성 |

⚠️ 용량 전제: LLM 로드 피크 12~14GB/16GB. 처방은 backend 단일 워커로 직렬화(contract §3). Next 빌드는 절대 서버에서 하지 않음.

## 마지막 머지 PR
- **PR #10** (이슈 #1) — BE 스캐폴드+JWT 인증. code-reviewer(opus) P1 2 → 2라운드 픽스 → 잔존 0 / security-reviewer(opus) P1 0. 테스트 27건(Testcontainers PG16)
- PR #8 (이슈 #5) — FE 스캐폴드+인증 화면

## 다음 작업
### P0 (Phase 1 — 백엔드 코어)
- [x] #1 [BE] 스캐폴드 + Auth(JWT) + 예외/ErrorCode 뼈대 (PR #10)
- [ ] #2 [BE] Farm 멀티테넌시 (멤버십·초대·테넌트 가드) — 진행 중
### P1 (Phase 1 — 도메인 + 프론트)
- [ ] #3 [BE] 진단 프록시 + 이력
- [ ] #4 [BE] 처방 비동기 job (단일 워커 직렬화)
- [x] #5 [FE] 스캐폴드 + 인증 화면 (PR #8)
- [ ] #6 [FE] 농장·진단·처방 화면 — 진행 중
### P2 (Phase 2 — 배포)
- [ ] #7 [INFRA] arm1 배포 (PG DB·systemd·nginx·DNS·certbot·CI/CD) — **nginx `limit_req`(login/refresh 브루트포스 방어) 포함**(BE 리뷰 P2-5)
- [ ] refresh_tokens 만료분 퍼지 스케줄러(BE 리뷰 P2-6, 컴포넌트 상이로 후속 분리)
- [ ] 환경 대시보드(오늘 운영 데이터) 연동 — 범위 미정, contract 갱신 필요

## 알려진 이슈 / 결정 기록
| 항목 | 내용 |
|---|---|
| DB 엔진 | PG16 확정(2026-08-19). arm1 mysqld는 6월 이전 잔재(구 community·mes·planner·smartfarm 스키마) — 신규 서비스와 무관, 추후 정리 후보 |
| 멀티테넌시 | Farm=테넌트, path `{farmId}` 입력값 취급·매 요청 멤버십 재검증 (contract §2) |
| 이미지 저장 | 1차 미저장(진단 결과만 이력화) |
| cropType | 1차 TOMATO 전용(ai-server 모델 제약), enum 확장 대비 |
