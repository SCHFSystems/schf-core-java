# SCHF Clean

Ambiente SCHF v2 limpo e instável — ideal para demonstração, desenvolvimento e certificação de instalação.

## Quick Start

```powershell
# Instalar (primeira execução)
.\install.ps1

# Configurar instância
curl -X POST http://localhost:8084/api/setup/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "organizationCode": "MINHA_ORG",
    "organizationName": "Minha Organização",
    "adminUsername": "admin",
    "adminEmail": "admin@example.com",
    "adminPassword": "change_me_local_dev_only_placeholder" # gitleaks:allow
  }'

# Verificar status
curl http://localhost:8084/api/setup/status
```

## Serviços

| Serviço | Imagem | Porta Interna |
|---------|--------|--------------|
| proxy   | caddy:2-alpine | 8084 (host) |
| api     | schf-core-java | 8080 |
| db      | postgres:16-alpine | 5432 |
| redis   | redis:7-alpine | 6379 |

## Scripts

| Script | Descrição |
|--------|-----------|
| `install.ps1` | Primeira instalação (cria diretórios, gera segredos, sobe stack) |
| `start.ps1` | Iniciar serviços |
| `stop.ps1` | Parar serviços |
| `status.ps1` | Status dos serviços e API |
| `backup.ps1` | Backup do banco PostgreSQL |
| `restore.ps1` | Restore com validação de checksum |
| `update.ps1` | Atualizar imagens e aplicar Flyway |
| `uninstall.ps1` | Remover containers (preserva dados por padrão) |

## Persistência

- Banco PostgreSQL: `D:\SCHF\_runtime\schf-clean\data\postgres`
- Redis: `D:\SCHF\_runtime\schf-clean\data\redis`
- Backups: `D:\SCHF\_runtime\schf-clean\backups`
- Logs: `D:\SCHF\_runtime\schf-clean\logs`
- Config: `D:\SCHF\_runtime\schf-clean\config`

## Segurança

- PostgreSQL e Redis não são expostos para a rede externa.
- Apenas o proxy publica a porta 8084.
- Container da API executa como usuário não-root.
- CORS por allowlist (configurável via `SCHF_ALLOWED_ORIGINS`).
- Healthchecks reais em todos os serviços.
