# smartfarm_service frontend — 홈서버 이전 파일럿(이슈 #27) 도커 이미지.
# next.config.ts는 수정하지 않는다 — NEXT_PUBLIC_API_URL 미설정 시 빌드가 실패하는 것은
# 의도된 가드(docs/api-contract.md §6)이므로 반드시 --build-arg로 전달해야 한다.

# ---- deps 스테이지 ----
FROM node:22-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

# ---- build 스테이지 ----
FROM node:22-alpine AS build
WORKDIR /app

ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN npm run build

# ---- 런타임 스테이지 ----
FROM node:22-alpine AS runtime
WORKDIR /app

RUN addgroup --system nodejs && adduser --system --ingroup nodejs nextjs

ENV NODE_ENV=production
# Next standalone server.js는 HOSTNAME 환경변수를 바인드 주소로 사용하는데, 컨테이너 셸이
# HOSTNAME을 컨테이너 호스트명으로 자동 설정해 localhost 헬스체크가 거부됨 → 0.0.0.0 명시 고정
ENV HOSTNAME=0.0.0.0 PORT=3000

# .github/workflows/deploy.yml:134-141 의 standalone 패키징 구조를 그대로 이식:
# .next/standalone(server.js + 필요한 node_modules 실체) + .next/static + public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

RUN chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 3000

# compose의 nginx가 depends_on: frontend: condition: service_healthy로 대기할 수 있도록(리뷰 P2-4)
# node:22-alpine의 busybox wget 사용 — curl 미포함
# 127.0.0.1 고정: alpine에서 localhost는 ::1(IPv6)로 먼저 풀리는데 Next는 IPv4만 리슨
HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=6 \
    CMD wget -q --spider http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]
