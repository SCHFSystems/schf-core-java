# SCHF Core Java

SCHF v2 backend foundation.

## Stack
- Java 21
- Spring Boot 3
- Maven
- Spring Web
- Spring Security baseline
- Spring Data JPA
- Flyway
- PostgreSQL
- Actuator
- Micrometer/Prometheus
- JUnit 5
- Testcontainers
- ArchUnit
- Docker Compose

## Scope
This repository is the clean Java foundation for SCHF v2.

It must not contain real Santa Casa data, `.env` files, dumps, FDB/FBK files, backups, raw logs, credentials, screenshots with sensitive data, or tokens.

## Run tests

```powershell
mvn verify
```

Integration tests use Testcontainers and require Docker Desktop running.

## Run locally with Docker Compose

```powershell
docker compose config
docker compose up -d --build
curl http://localhost:8088/actuator/health
curl http://localhost:8088/api/health
docker compose down
```

Do not use `docker compose down -v` unless explicitly approved.

## Local environment
Copy `.env.example` to `.env` only for local development.

Never commit `.env`.
