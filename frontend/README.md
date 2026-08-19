# smartfarm_service — frontend

Next.js(App Router) · TypeScript · Tailwind 프론트엔드. 회원 인증, 농장(테넌트) 관리,
잎 사진 진단·LLM 처방 화면을 제공한다.

## 로컬 실행

```bash
NEXT_PUBLIC_API_URL=http://localhost:8085 npm run dev
```

`NEXT_PUBLIC_API_URL`은 backend(Spring Boot) 주소다. 미설정 시 프로덕션 빌드(`npm run build`)가
실패하도록 `next.config.ts`에서 강제한다.

## 빌드

```bash
NEXT_PUBLIC_API_URL=https://farm.luma200ok.com npm run build
```

`output: "standalone"`으로 빌드되어 systemd 등에서 `node .next/standalone/server.js`로 실행한다.

## 문서

- [API Contract(단일 진실)](../docs/api-contract.md)
- [진행 현황(STATUS)](../docs/STATUS.md)
