# Container Security

## Dockerfile (multi-stage)

O build usa duas etapas:

1. **Build stage**: `maven:3.9.9-eclipse-temurin-21` — compila o JAR.
2. **Runtime stage**: `eclipse-temurin:21-jre` — imagem final mínima.

A imagem runtime **não contém**:
- Maven cache
- Código-fonte
- Compilador
- Secrets
- `.env`

## Usuário não-root

O container da API executa como usuário `schf` (UID 1001, GID 1001).

```dockerfile
RUN groupadd --system --gid 1001 schf \
    && useradd --system --gid schf --uid 1001 --no-create-home --shell /sbin/nologin schf
...
USER schf
```

## Healthcheck

```dockerfile
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
```

## .dockerignore

Exclui do contexto de build:
- `target/`, `.git/`, `.env`, logs
- Documentos, README, AGENTS.md
- Arquivos de banco (`*.FDB`, `*.FBK`, `*.dump`)
- Bundles (`*.schf`)
- SQL fora de migrations

## Compose hardening

- Serviços `db` e `redis` sem porta publicada no host.
- Proxy Caddy com headers de segurança:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy` restrita
  - `-Server` (oculta versão do servidor)
- Logs com rotação (10 MB, 3 arquivos).
- `restart: unless-stopped` em todos os serviços.

## Melhorias futuras (não aplicadas nesta sprint)

- `readOnlyRootFilesystem: true` na API (bloqueia escrita exceto tmpfs).
- `tmpfs` para `/tmp` e `/app/logs`.
- `cap_drop: ALL` com `cap_add: [NET_BIND_SERVICE]`.
- `security_opt: [no-new-privileges:true]`.
- Limites de CPU/memória documentados.

Estas melhorias dependem de ajustes no Flyway (escrita temporária) e no upload de bundles. Serão avaliadas em sprint dedicada de hardening.
