# SmartFarm Service

[smartfarm_ai](https://github.com/luma200ok/smartfarm_ai)(ML/DL/LLM 스마트팜 AI)를 멀티테넌시 웹 서비스로 확장한 3-tier 프로젝트.

## 구성
```
backend/    Spring Boot 3.x · Java 21 · JPA · PostgreSQL 16 · JWT
frontend/   Next.js (App Router) · TypeScript · Tailwind
docs/       api-contract.md(API 단일 진실) · STATUS.md(진행 현황)
```
AI 추론(잎 병해 진단·LLM 처방)은 기존 smartfarm_ai 레포의 FastAPI ai-server를 내부 호출로 재사용한다.

## 핵심 기능 (1차)
- 회원/JWT 인증, 농장(테넌트) 생성·초대코드 멤버십
- 잎 사진 진단(CNN·YOLO) + 농장별 이력
- LLM 처방(비동기 job, 로컬 Ollama) + 이력

## 문서
- [API Contract](docs/api-contract.md)
- [진행 현황(STATUS)](docs/STATUS.md)
