# 홈서버 이전 파일럿(이슈 #27) — 1회 세팅 체크리스트

이 문서는 **홈서버(x86, self-hosted runner)에서 메타(A)가 직접** 실행하는 1회 세팅 절차다.
이 PR(#27 PR-1)은 도커화 산출물(Dockerfile·compose·nginx.conf)만 추가하며, 아래 절차의 실제
실행(runner 설치·터널 생성 등)은 별도 PR/작업으로 진행한다 — 이 문서는 그 실행 가이드다.

참조: `docs/api-contract.md`(환경변수·CORS), 기존 OCI 배포는 `deploy/README.md`(arm1, 병행 유지).

## 0. 전제

- 홈서버는 x86_64, Docker + docker compose plugin 설치돼 있음.
- `db-postgres`(PostgreSQL), `smartfarm-ai`(AI 서버) 컨테이너가 이미 홈서버에서 별도 compose로
  떠 있다고 가정(`~/srv/db/compose.yml` 등) — 이 스택은 신규로 만들지 않고 `shared-net`으로만 연결한다.
- OCI arm1 배포(`deploy/README.md`)는 그대로 유지 — 이 파일럿은 병행 검증 단계이며 트래픽 전환은
  별도 결정 사항이다.

## 1. 공유 네트워크 생성

```
docker network create shared-net
```

- [ ] `~/srv/db/compose.yml`의 postgres 서비스(`db-postgres`)에 `shared-net`을 조인하도록 추가
      (`networks: [default, shared-net]` 형태 — 이 레포의 `deploy/home/compose.yml` backend 서비스와 동일 패턴)
      후 `docker compose -f ~/srv/db/compose.yml up -d`로 재적용
- [ ] `smartfarm-ai` 컨테이너도 필요 시 동일하게 `shared-net` 조인 확인(AI_SERVER_URL이 컨테이너명으로
      접근하므로 같은 네트워크에 있어야 함)

## 2. `.env` 작성

```
mkdir -p ~/srv/smartfarm
cp deploy/home/.env.example ~/srv/smartfarm/.env
# ~/srv/smartfarm/.env 를 편집기로 열어 실제 값 채우기(SPRING_DATASOURCE_PASSWORD, JWT_SECRET, TUNNEL_TOKEN 등)
chmod 600 ~/srv/smartfarm/.env
```

- [ ] `docker compose --env-file ~/srv/smartfarm/.env -f deploy/home/compose.yml ...`처럼 매 명령마다
      `--env-file`을 지정하거나, 이 레포를 홈서버에 클론한 뒤 `deploy/home/.env`로 심링크/복사해
      compose 기본 탐색 경로(같은 디렉터리)를 활용한다.
- [ ] `JWT_SECRET`은 충분히 긴 랜덤 값(`openssl rand -base64 48`)
- [ ] 절대 `.env`를 git에 커밋하지 말 것(루트 `.gitignore`가 `.env`/`.env.*`를 이미 차단, `.env.example`만 예외)

## 3. 이미지 저장 디렉터리

```
mkdir -p ~/srv/smartfarm/images
sudo chown -R 1000:1000 ~/srv/smartfarm/images
```

- [ ] `deploy/home/compose.yml`의 backend 볼륨(`~/srv/smartfarm/images:/data/images`)과 경로 일치 확인
- [ ] `chown` UID/GID 1000은 `backend/Dockerfile`의 비루트 유저(spring)와 고정 매칭된다(리뷰 P1) —
      컨테이너 내부에서 쓰기 권한이 필요하므로 반드시 먼저 실행. 검증: `docker run --rm <backend 이미지> id -u` → `1000`
- [ ] 디스크 여유 공간 확인(진단 이미지 누적)

## 4. GitHub self-hosted runner 설치 (라벨 `home`)

- [ ] 레포 Settings → Actions → Runners → New self-hosted runner, 라벨에 `home` 추가
- [ ] **비루트 전용 계정**으로 설치(예: `runner` 유저) + `docker` 그룹에 추가
      (`sudo usermod -aG docker runner`) — runner 프로세스가 루트 권한 없이 docker 명령 실행 가능하도록
- [ ] 이 파일럿의 배포 워크플로우는 **`pull_request` 트리거를 절대 사용하지 않는다** — self-hosted
      runner에서 fork PR의 워크플로우가 실행되면 PR 작성자가 임의 코드를 runner(홈 네트워크 접근 가능)에서
      실행시킬 수 있어 원격 코드 실행(RCE)/내부망 피벗 위험이 있다(GitHub Actions 공식 보안 권고사항).
      `push`(main, 보호된 브랜치) 또는 `workflow_dispatch`만 사용 — 이는 후속 워크플로우 PR에서 강제한다.
- [ ] runner 서비스로 등록(`svc.sh install && svc.sh start`)해 재부팅 후에도 유지

## 5. Cloudflare Tunnel 생성

- [ ] Cloudflare Zero Trust 대시보드 → Networks → Tunnels → Create a tunnel(이름 예: `smartfarm-home`)
- [ ] 생성된 토큰을 `~/srv/smartfarm/.env`의 `TUNNEL_TOKEN`에 채움(§2)
- [ ] Public Hostname 추가: `farm-home.luma200ok.com` → Service `http://nginx:80`
      — **컨테이너 내부 라우팅이므로 대상은 nginx 컨테이너의 80 포트**, cloudflared 컨테이너가
      compose의 `default` 네트워크로 nginx에 서비스명 DNS로 접근한다(호스트 포트 불필요)
- [ ] DNS 탭에서 `farm-home.luma200ok.com` CNAME이 자동 생성됐는지 확인

## 6. 스모크 절차

```
cd deploy/home
docker compose build
docker compose up -d
```

- [ ] `docker compose ps` — backend healthy, frontend/nginx/cloudflared running 확인
- [ ] `curl -s http://127.0.0.1:8088/api/health` → `{"status":"ok"}` (nginx 경유, 홈서버 로컬에서만 접근 가능)
- [ ] `curl -I http://127.0.0.1:8088/` → 200 (frontend)
- [ ] `curl -s https://farm-home.luma200ok.com/api/health` (터널 경유, 외부에서) → 동일 응답
- [ ] `docker compose logs backend | grep -i flyway` — 마이그레이션이 정상 적용됐는지 확인
- [ ] 회원가입 → 로그인 → 농장 생성까지 1회 수동 스모크(파일럿이므로 운영 데이터 아님, 확인 후 정리 가능)

## 7. 알려진 제약 / 후속

- 이 PR은 도커화 산출물만 포함 — self-hosted runner용 배포 워크플로우(`.github/workflows/deploy-home.yml`
  등)는 별도 PR(#27 PR-2 이후)에서 추가한다.
- `docker compose logs`의 백엔드 로그는 stdout이라 systemd/journalctl 기반이 아님 — 로그 보존 정책은
  후속 검토 필요(예: `logging: driver: json-file, options: max-size`).
- OCI arm1과 홈서버 두 곳에 동시에 서비스가 뜨는 동안 DB는 서로 다른 인스턴스(arm1=native PG,
  홈서버=`db-postgres` 컨테이너)이므로 데이터가 동기화되지 않는다 — 트래픽 전환 전 이관 계획 필요.
