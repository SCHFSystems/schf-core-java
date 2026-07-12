# SCHF Clean Deployment

## Topologia

```
Cliente/PWA
    |
    v
Proxy (Caddy :8084) ──> API (Java :8080) ──> PostgreSQL (interno)
    |                                             |
    └──> headers de segurança                     Redis (interno)
    └──> CORS por allowlist
    └──> rate limiting (proxy)
```

O proxy é o único serviço com porta publicada no host. Banco e Redis ficam em rede interna Docker.

## Pré-requisitos

- Docker Engine 24+
- Docker Compose v2
- Windows (scripts PowerShell) ou Linux (equivalentes manuais)

## Estrutura de diretórios

```
deploy/schf-clean/
  compose.yaml         # Docker Compose principal
  .env.example         # Template de configuração
  caddy/
    Caddyfile          # Configuração do proxy
  install.ps1          # Primeira instalação
  start.ps1            # Iniciar stack
  stop.ps1             # Parar stack
  status.ps1           # Status completo
  backup.ps1           # Backup PostgreSQL
  restore.ps1          # Restore com validação
  update.ps1           # Atualização segura
  uninstall.ps1        # Remoção controlada
```

## Variáveis de ambiente

| Variável | Descrição | Obrigatória |
|----------|-----------|-------------|
| `SCHF_INSTANCE_ID` | Identificador único da instância | Sim (gerada no install) |
| `SCHF_INSTANCE_NAME` | Nome amigável da instância | Sim |
| `SCHF_ENVIRONMENT` | Ambiente (production, staging, dev) | Sim |
| `SCHF_PUBLIC_URL` | URL pública para CORS e links | Sim |
| `SCHF_PORT` | Porta do proxy no host (padrão 8084) | Não |
| `SCHF_DATABASE_PASSWORD` | Senha do PostgreSQL | Sim |
| `SCHF_REDIS_PASSWORD` | Senha do Redis | Sim |
| `SCHF_JWT_SECRET` | Chave secreta JWT (mínimo 64 chars) | Sim |
| `SCHF_ALLOWED_ORIGINS` | Origens CORS permitidas | Sim |
| `SCHF_BACKUP_DIRECTORY` | Diretório de backups | Não |

## Portas

| Serviço | Host | Container | Protocolo |
|---------|------|-----------|-----------|
| Proxy | 8084 (configurável) | 8080 | HTTP |
| API | — | 8080 | HTTP |
| PostgreSQL | — | 5432 | TCP |
| Redis | — | 6379 | TCP |

Nunca exponha PostgreSQL ou Redis diretamente.

## Healthchecks

Todos os serviços têm healthcheck real:

- **Proxy**: `wget --spider http://localhost:8080/api/health`
- **API**: `curl http://localhost:8080/actuator/health/readiness`
- **PostgreSQL**: `pg_isready`
- **Redis**: `redis-cli ping` (com autenticação)

O Compose respeita `depends_on` com `condition: service_healthy`.
