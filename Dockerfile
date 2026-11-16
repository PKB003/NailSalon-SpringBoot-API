# =====================================================================
# Build stage — dùng Maven + JDK 17 (không phụ thuộc wrapper line-ending)
# =====================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

# Cache dependency layer
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# =====================================================================
# Runtime stage — JRE 17 (Liberica Alpine, ~52MB), non-root, healthcheck
# =====================================================================
FROM bellsoft/liberica-openjre-alpine:17

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --chown=app:app --from=build /workspace/target/*.jar app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
