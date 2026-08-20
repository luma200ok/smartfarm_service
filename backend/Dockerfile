# smartfarm_service backend — 홈서버 이전 파일럿(이슈 #27) 도커 이미지.
# application.yml은 수정하지 않는다 — 컨테이너 바인딩(0.0.0.0)은 compose에서
# SERVER_ADDRESS env로 오버라이드한다(Spring Boot relaxed binding: SERVER_ADDRESS -> server.address).

# ---- 빌드 스테이지 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# gradle wrapper·설정 파일 먼저 복사해 의존성 레이어 캐시 극대화
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# ---- 런타임 스테이지 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# compose 헬스체크(curl http://localhost:8085/api/health)용 — 베이스 이미지에 기본 미포함
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# UID/GID 1000 고정 — 바인드 마운트(~/srv/smartfarm/images -> /data/images)와 호스트 소유권을 맞추기 위해
# (리뷰 P1: --system 유저는 UID가 서버마다 달라질 수 있어 호스트에서 미리 chown할 수 없다)
# 베이스 이미지(Ubuntu 기반)에 이미 uid/gid 1000인 기본 "ubuntu" 계정이 있어 충돌하므로 먼저 제거한다.
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && (groupdel ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 spring \
    && useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin spring

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN chown -R spring:spring /app
USER spring

EXPOSE 8085

# JAVA_OPTS로 힙 상한 등을 compose에서 주입할 수 있도록 shell 형태로 실행(리뷰 P2-1)
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
