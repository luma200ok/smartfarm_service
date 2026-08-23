#!/bin/bash
# smartfarm 홈서버 배포 헬퍼 — runner 유저가 sudo로 실행하는 유일한 진입점.
# 설치: sudo install -o root -g root -m 755 deploy/home/smartfarm-deploy.sh /usr/local/bin/smartfarm-deploy
# 배포 입력은 러너 워크스페이스가 아닌 root 소유 REPO_DIR(origin/main 직접 체크아웃)만 사용
# — runner가 쓸 수 있는 파일을 root가 실행하는 경로를 차단(compose 변조 → root 승격 방지).
set -euo pipefail
[ $# -eq 1 ] || { echo "usage: smartfarm-deploy {up|ps|prune|diagnose}" >&2; exit 64; }
REPO_DIR=/opt/smartfarm/repo
ENV_FILE=/home/jb/srv/smartfarm/.env
LOG_DIR=/var/log/smartfarm-deploy
COMPOSE=(docker compose -f "$REPO_DIR/deploy/home/compose.yml" --env-file "$ENV_FILE")
case "$1" in
  up)
    git -C "$REPO_DIR" fetch --depth 1 origin main
    git -C "$REPO_DIR" checkout --detach --force FETCH_HEAD
    exec "${COMPOSE[@]}" up -d --build ;;
  ps)    exec "${COMPOSE[@]}" ps ;;
  prune) exec docker image prune -f --filter "until=72h" ;;
  diagnose)
    # 전체 로그는 호스트에만 저장(root 600) — stdout(공개 Actions 로그)에는 상태 요약만 출력한다
    mkdir -p "$LOG_DIR" && chmod 700 "$LOG_DIR"
    "${COMPOSE[@]}" logs --tail=200 > "$LOG_DIR/last-failure.log" 2>&1 || true
    chmod 600 "$LOG_DIR/last-failure.log" || true
    "${COMPOSE[@]}" ps || true
    # backend는 container_name 미지정 — 프로젝트명(smartfarm-home) 기반 이름이므로 ps -q로 안전 조회
    # 서비스 키는 smartfarm-backend 다(#97 — 공유망 일반명 충돌 회피).
    BACKEND_ID="$("${COMPOSE[@]}" ps -q smartfarm-backend 2>/dev/null || true)"
    if [ -n "$BACKEND_ID" ]; then
      docker inspect "$BACKEND_ID" --format 'backend health: {{json .State.Health.Status}}' || true
    fi
    echo "full logs saved on host: $LOG_DIR/last-failure.log" ;;
  *)     echo "usage: smartfarm-deploy {up|ps|prune|diagnose}" >&2; exit 64 ;;
esac
