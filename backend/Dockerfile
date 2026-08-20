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

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN chown -R spring:spring /app
USER spring

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
