FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

COPY src src
COPY modules modules

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test


FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=builder /build/build/libs/*.jar app.jar

RUN chown -R app:app /app

EXPOSE 8085

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

USER app

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
