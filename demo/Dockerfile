# 1단계: Gradle을 이용한 애플리케이션 빌드 환경 (Java 21)
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /app

# 빌드 속도 향상을 위한 레이어 캐싱 (설정 파일 먼저 복사)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

# Gradle 빌드 실행 (테스트 제외 및 실행 가능한 JAR 생성)
RUN gradle build --no-daemon -x test

# [핵심 수정] plain.jar를 지워버리고, 남은 실제 실행 가능한 JAR 파일 이름을 project.jar로 통일합니다.
RUN rm -f build/libs/*-plain.jar && mv build/libs/*.jar build/libs/project.jar

# 2단계: 실제 실행 환경 (경량화된 JRE 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 1단계에서 빌드된 jar 파일 복사
#COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
# 명확하게 지정된 project.jar만 컨테이너로 가져옵니다.
COPY --from=build /app/build/libs/project.jar app.jar

RUN mkdir -p /app/cards
# 컨테이너 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]