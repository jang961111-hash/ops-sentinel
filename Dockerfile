# US-016: Docker Compose(app+postgres) 원커맨드 기동용 멀티스테이지 빌드.
# 1단계: Gradle로 jar 빌드. 2단계: JRE(런타임만)로 실행 이미지 경량화.

# ---- Build stage ----
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

# 의존성 캐시 레이어 분리: 소스보다 먼저 build.gradle/settings.gradle만 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

COPY src ./src
RUN gradle build -x test --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
