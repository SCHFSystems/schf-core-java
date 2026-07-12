FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 1001 schf \
    && useradd --system --gid schf --uid 1001 --no-create-home --shell /sbin/nologin schf
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build --chown=schf:schf /workspace/target/schf-core-java-*.jar /app/schf-core-java.jar
USER schf
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-jar", "/app/schf-core-java.jar"]
