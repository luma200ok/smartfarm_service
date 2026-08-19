# 배포 — 서버 최초 세팅 체크리스트 (A 실행용)

이 문서의 명령은 **메타(A)가 oci-arm1에 직접 SSH 접속해서** 실행한다. 이 레포(`smartfarm_service`)의
구현 서브는 서버에 접속하지 않는다 — 여기 나열된 파일(`deploy/*.service`, `deploy/nginx-farm.conf`)은
레포에 있는 "적용용 원본"이고, 실제 설치·값 채우기는 A의 몫이다.

참조: `docs/STATUS.md` 인프라 표, `docs/api-contract.md` §0(아키텍처)·§6(환경변수).

## 0. 전제

- arm1은 3코어/16GB, LLM(Ollama) 로드 피크 12~14GB — backend `-Xmx512m` 고정, frontend/backend 모두
  스케일아웃 금지(docs/STATUS.md).
- Next.js는 **서버에서 빌드하지 않는다** — GitHub Actions가 빌드한 standalone 산출물만 배포한다.

## 1. PostgreSQL 16 — DB 생성

- [ ] `smartfarm_service` 데이터베이스 신설 (기존 hajacheck 도커 PG와 별개 — 네이티브 PG16)
- [ ] 전용 애플리케이션 유저 생성, `smartfarm_service` DB에 대한 권한만 부여(최소 권한)
- [ ] Flyway가 첫 기동 시 마이그레이션을 실행하므로 스키마 사전 생성 불필요(`spring.flyway.out-of-order=true`)

## 2. 시크릿 — `/etc/app-secrets/smartfarm-service.env` (root:600)

**값은 여기 적지 말 것 — 키 이름만.** 실제 값은 A가 서버에서 직접 생성/입력한다.

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_SECRET=
CORS_ALLOWED_ORIGINS=https://farm.luma200ok.com
AI_SERVER_URL=http://127.0.0.1:8000
```

- [ ] 파일 생성 + `chown root:root` + `chmod 600`
- [ ] `SPRING_DATASOURCE_URL`은 기본값(`jdbc:postgresql://127.0.0.1:5432/smartfarm_service`, application.yml)이
      맞다면 env 파일에 넣지 않아도 됨 — 다르면 여기 추가
- [ ] `JWT_SECRET`은 충분히 긴 랜덤 값(openssl rand -base64 48 등)

## 3. 앱 디렉터리

```
/home/opc/apps/smartfarm-service/
  app.jar                 (backend 배포 대상)
  incoming/                (backend scp 임시 업로드 경로 — deploy.yml)
  frontend/
    frontend-dist.tar.gz   (frontend scp 임시 업로드 경로 — deploy.yml)
    releases/<timestamp>/  (frontend 배포마다 새 디렉터리)
    current -> releases/<최신>  (심링크, deploy.yml이 원자 교체)
```

- [ ] `mkdir -p /home/opc/apps/smartfarm-service/incoming /home/opc/apps/smartfarm-service/frontend/releases`
- [ ] 소유자 `opc:opc`

## 4. Java 21 / Node 22 설치 (arm1)

- [ ] `java -version`으로 21 확인 — **arm1 실측 Java 21.0.11 설치돼 있음**(미설치 시 `dnf install java-21-openjdk`)
- [ ] NodeSource 또는 nvm으로 Node 22 LTS 설치 (frontend systemd 유닛이 `/usr/bin/node`를 직접 호출하므로
      PATH에 걸리는 전역 설치 또는 `/usr/bin/node` 심링크 필요)
- [ ] `node -v` 확인

## 5. systemd 유닛 설치

- [ ] `deploy/smartfarm-service-backend.service` → `/etc/systemd/system/`
- [ ] `deploy/smartfarm-service-frontend.service` → `/etc/systemd/system/`
- [ ] `sudo systemctl daemon-reload`
- [ ] `sudo systemctl enable smartfarm-service-backend smartfarm-service-frontend`
- [ ] 최초 배포(§7) 후 `sudo systemctl start smartfarm-service-backend smartfarm-service-frontend`

## 6. nginx

**arm1 실측**: `/etc/nginx/nginx.conf`가 `include /etc/nginx/conf.d/*.conf;`만 사용 —
sites-available/sites-enabled 구조 없음(hajacheck.conf 등 기존 설정 전부 `conf.d/` 평면 구조). 이 구조를 따른다.

- [ ] `sudo mkdir -p /etc/nginx/snippets` (RHEL nginx는 기본 미제공)
- [ ] nginx-farm.conf 하단 주석의 `proxy_set_header` 4줄을 `/etc/nginx/snippets/proxy-common.conf`로 저장
- [ ] `deploy/nginx-farm.conf` → `/etc/nginx/conf.d/farm.conf` 로 복사
      (`limit_req_zone`은 파일 최상단에 있어도 `http` 컨텍스트로 include되므로 이 구조에서 그대로 유효)
- [ ] `sudo nginx -t` → `sudo systemctl reload nginx`

## 7. DNS + certbot

- [ ] Cloudflare에 `farm.luma200ok.com` A(+AAAA) 레코드 → arm1 IP (기존 서비스와 동일 방식)
- [ ] `sudo certbot --nginx -d farm.luma200ok.com` (80 서버 블록에 443 + HTTP->HTTPS 리다이렉트 자동 추가)
- [ ] 인증서 자동 갱신(certbot systemd timer) 기존 동작 확인만 — 별도 설정 불필요

## 8. GitHub Actions 시크릿 등록

레포 Settings → Secrets and variables → Actions:

- [ ] `OCI_HOST` — arm1 공인 IP
- [ ] `OCI_USER` — `opc`
- [ ] `OCI_SSH_KEY` — 배포 전용 SSH 개인키(가능하면 배포 전용 키 신규 발급, 기존 관리용 키 재사용 지양)

## 9. 최초 배포 검증

- [ ] main에 backend/frontend 변경을 머지해 `.github/workflows/deploy.yml` 최초 실행 확인
      (또는 `workflow_dispatch`로 수동 실행)
- [ ] 배포 후 `sudo systemctl status smartfarm-service-backend smartfarm-service-frontend`
- [ ] `curl -I https://farm.luma200ok.com/` (프론트) / `curl -s https://farm.luma200ok.com/api/health`
      → 200 `{"status":"ok"}` 이면 backend 정상(HealthController, 무인증)
- [ ] 회원가입 → 로그인 → 농장 생성 → 진단 업로드까지 브라우저로 1회 수동 스모크

## 10. 롤백 절차

- **backend**: `deploy.yml`이 교체 전 `app.jar.bak`(직전 1세대)을 남긴다.
  `cp -f app.jar.bak app.jar && sudo systemctl restart smartfarm-service-backend`로 즉시 복귀.
- **frontend**: `releases/<이전 타임스탬프>`가 남아있으므로
  `ln -sfn releases/<이전 타임스탬프> current.tmp && mv -Tf current.tmp current && sudo systemctl restart smartfarm-service-frontend`.

## 11. 알려진 후속 (STATUS.md 참조)

- ai-server 질문 이력이 farm/user 구분 없이 자체 DB에 혼합 저장됨 — 배포 전 보존정책 검토 필요
  (smartfarm_ai src/llm/history.py, 이 레포 범위 밖)
- refresh_tokens 만료분 퍼지 스케줄러는 이 이슈 범위 밖(#7 후속으로 STATUS.md에 별도 기록됨)
