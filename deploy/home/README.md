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
      (`networks: [default, shared-net]` 형태 — 이 레포의 `deploy/home/compose.yml` `smartfarm-backend` 서비스와 동일 패턴)
      후 `docker compose -f ~/srv/db/compose.yml up -d`로 재적용
- [ ] `smartfarm-ai` 컨테이너도 필요 시 동일하게 `shared-net` 조인 확인(AI_SERVER_URL이 컨테이너명으로
      접근하므로 같은 네트워크에 있어야 함)

## 2. `.env` 작성

```
mkdir -p ~/srv/smartfarm
cp deploy/home/.env.example ~/srv/smartfarm/.env
# ~/srv/smartfarm/.env 를 편집기로 열어 실제 값 채우기(SPRING_DATASOURCE_PASSWORD, JWT_SECRET, TUNNEL_TOKEN 등)
chmod 700 ~/srv/smartfarm
chmod 600 ~/srv/smartfarm/.env
```

- [ ] **소유·접근 모델**: `.env`는 관리자 `jb` 소유 600 + 디렉터리(`~jb/srv/smartfarm`) 700 —
      `runner` 계정은 접근 불가하고, 배포 시엔 sudo 헬퍼(§4)가 root 권한으로 읽어 `--env-file`로
      compose에 공급한다. runner(=워크플로우 코드)는 시크릿 값을 볼 수 없다.
- [ ] `JWT_SECRET`은 충분히 긴 랜덤 값(`openssl rand -base64 48`)
- [ ] 절대 `.env`를 git에 커밋하지 말 것(루트 `.gitignore`가 `.env`/`.env.*`를 이미 차단, `.env.example`만 예외)

## 3. 이미지 저장 디렉터리

```
mkdir -p ~/srv/smartfarm/images
sudo chown -R 1000:1000 ~/srv/smartfarm/images
```

- [ ] `deploy/home/compose.yml`의 `smartfarm-backend` 볼륨(`${DATA_DIR}/images:/data/images`)과 경로 일치 확인
      — `.env`의 `DATA_DIR`(절대경로, 예: `/home/jb/srv/smartfarm`)이 이 디렉터리를 가리켜야 한다
- [ ] `chown` UID/GID 1000은 `backend/Dockerfile`의 비루트 유저(spring)와 고정 매칭된다(리뷰 P1) —
      컨테이너 내부에서 쓰기 권한이 필요하므로 반드시 먼저 실행. 검증: `docker run --rm <backend 이미지> id -u` → `1000`
- [ ] 디스크 여유 공간 확인(진단 이미지 누적)

## 4. GitHub self-hosted runner 설치 (라벨 `home`)

- [ ] 레포 Settings → Actions → Runners → New self-hosted runner, 라벨에 `home` 추가
- [ ] **전용 `runner` 계정**으로 설치 — `jb`가 아닌 별도 비루트 계정이며, **`docker` 그룹에 넣지 않는다**.
      runner(=워크플로우 코드)가 docker 소켓·시크릿에 직접 접근하지 못하게 권한을 분리하는 것이 목적.
- [ ] **배포 입력용 레포 최초 1회 root clone** — 헬퍼는 러너 워크스페이스가 아닌 root 소유 사본에서만
      compose를 실행한다(runner가 쓸 수 있는 파일을 root가 실행하는 경로 차단):

      ```
      sudo mkdir -p /opt/smartfarm
      sudo git clone https://github.com/luma200ok/smartfarm_service.git /opt/smartfarm/repo
      ```
- [ ] **헬퍼 설치**(레포 버전관리본 `deploy/home/smartfarm-deploy.sh` — 갱신 시에도 동일 명령 재실행):

      ```
      sudo install -o root -g root -m 755 deploy/home/smartfarm-deploy.sh /usr/local/bin/smartfarm-deploy
      ```
- [ ] 배포 실행 권한은 sudo 헬퍼 **1개만, 인자까지 고정**해 허용 — sudoers(`visudo -f /etc/sudoers.d/smartfarm-deploy`):

      ```
      runner ALL=(root) NOPASSWD: /usr/local/bin/smartfarm-deploy up, /usr/local/bin/smartfarm-deploy ps, /usr/local/bin/smartfarm-deploy prune, /usr/local/bin/smartfarm-deploy diagnose
      ```

      헬퍼는 `/opt/smartfarm/repo`에 origin/main을 fetch/checkout한 뒤 그 안의 compose를 root로 실행하고
      `.env`(§2, runner 접근 불가)를 `--env-file`로 공급한다 — 워크플로우는
      `sudo -n /usr/local/bin/smartfarm-deploy <서브커맨드>`만 호출하며 임의 docker 명령·시크릿 열람이 불가능하다.
- [ ] 이 파일럿의 배포 워크플로우는 **`pull_request` 트리거를 절대 사용하지 않는다** — self-hosted
      runner에서 fork PR의 워크플로우가 실행되면 PR 작성자가 임의 코드를 runner(홈 네트워크 접근 가능)에서
      실행시킬 수 있어 원격 코드 실행(RCE)/내부망 피벗 위험이 있다(GitHub Actions 공식 보안 권고사항).
      현행 `.github/workflows/deploy-home.yml`은 `workflow_dispatch`만 사용하고 main ref 가드를 건다.
- [ ] runner 서비스로 등록(`svc.sh install && svc.sh start`)해 재부팅 후에도 유지

## 5. Cloudflare Tunnel 생성

- [ ] Cloudflare Zero Trust 대시보드 → Networks → Tunnels → Create a tunnel(이름 예: `smartfarm-home`)
- [ ] 생성된 토큰을 `~/srv/smartfarm/.env`의 `TUNNEL_TOKEN`에 채움(§2)
- [ ] Public Hostname 추가: `farm.luma200ok.com` → Service **`http://smartfarm-nginx:80`**
      — **컨테이너 내부 라우팅이므로 대상은 nginx 컨테이너의 80 포트**다. 터널은 이 스택 밖의
      독립 스택(luma200ok/home-infra 의 tunnel)이며 `shared-net` 으로 도달한다(#75).
      ⚠️ 서비스명은 반드시 **고유명**(`smartfarm-nginx`)을 쓴다 — `nginx` 같은 일반명은
      공유망에서 다른 스택과 충돌해 502 를 낸다(#97, luma200ok/home-infra#15)
- [ ] DNS 탭에서 `farm-home.luma200ok.com` CNAME이 자동 생성됐는지 확인

## 6. 스모크 절차

배포는 항상 헬퍼 경유로 실행한다(§4의 배포 경로와 동일 — origin/main 기준 빌드·기동):

```
sudo /usr/local/bin/smartfarm-deploy up
```

- [ ] `sudo /usr/local/bin/smartfarm-deploy ps` — backend healthy, frontend/nginx/cloudflared running 확인
- [ ] `curl -s http://127.0.0.1:8088/api/health` → `{"status":"ok"}` (nginx 경유, 홈서버 로컬에서만 접근 가능)
- [ ] `curl -I http://127.0.0.1:8088/` → 200 (frontend)
- [ ] `curl -s https://farm-home.luma200ok.com/api/health` (터널 경유, 외부에서) → 동일 응답
- [ ] Flyway 확인(관리자가 호스트에서 직접): `sudo docker compose -f /opt/smartfarm/repo/deploy/home/compose.yml --env-file ~jb/srv/smartfarm/.env logs backend | grep -i flyway`
- [ ] 회원가입 → 로그인 → 농장 생성까지 1회 수동 스모크(파일럿이므로 운영 데이터 아님, 확인 후 정리 가능)

## 7. 롤백 (수동, 관리자)

`prune`은 `--filter until=72h`라 **직전 배포의 dangling 이미지가 3일간 보존**된다 — 그 안에서는
재빌드 없이 이미지 되돌리기가 가능하다. (정석은 revert 커밋을 main에 머지한 뒤 워크플로우 재실행 —
아래는 응급용.)

```
sudo docker images --filter dangling=true          # 직전 backend/frontend 이미지 ID 확인(CREATED 시각으로 식별)
sudo docker tag <직전 backend 이미지ID> smartfarm-home-backend:latest
sudo docker tag <직전 frontend 이미지ID> smartfarm-home-frontend:latest
sudo docker compose -f /opt/smartfarm/repo/deploy/home/compose.yml --env-file ~jb/srv/smartfarm/.env up -d --no-build
# --no-build: 되돌린 태그 그대로 컨테이너 재생성(빌드 생략)
```

- 이미지 이름은 compose 프로젝트명(`name: smartfarm-home`) 기반 `smartfarm-home-{서비스}`로 고정된다.
- 롤백 후에도 `curl -s http://127.0.0.1:8088/api/health` 스모크(§6)를 반복해 확인한다.

## 8. 알려진 제약 / 후속

- 배포 워크플로우는 `.github/workflows/deploy-home.yml`(#27 PR-2)로 추가됨 — `workflow_dispatch` 전용이며
  push(main) 자동 트리거는 파일럿 안정화 후 별도 PR에서 추가 예정.
- `docker compose logs`의 백엔드 로그는 stdout이라 systemd/journalctl 기반이 아님 — 로그 보존 정책은
  후속 검토 필요(예: `logging: driver: json-file, options: max-size`).
- OCI arm1과 홈서버 두 곳에 동시에 서비스가 뜨는 동안 DB는 서로 다른 인스턴스(arm1=native PG,
  홈서버=`db-postgres` 컨테이너)이므로 데이터가 동기화되지 않는다 — 트래픽 전환 전 이관 계획 필요.
