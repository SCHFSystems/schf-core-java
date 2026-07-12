# Instance Configuration

## Visão geral

As variáveis de instância controlam a identidade do servidor SCHF e são expostas via `GET /api/system/info` de forma sanitizada.

## Variáveis

| Variável de ambiente | Campo em /api/system/info | Descrição |
|---------------------|--------------------------|-----------|
| `SCHF_INSTANCE_ID` | `instanceId` | UUID único da instância (gerado na instalação) |
| `SCHF_INSTANCE_NAME` | `instanceName` | Nome amigável (ex: "Hospital Santa Casa") |
| `SCHF_ENVIRONMENT` | `environment` | production, staging, dev |

## /api/system/info

Endpoint público, sem autenticação.

```json
{
  "productName": "SCHF Core",
  "instanceName": "Minha Instancia",
  "instanceId": "uuid-da-instancia",
  "apiVersion": "0.1.0-SNAPSHOT",
  "serverVersion": "0.1.0-SNAPSHOT",
  "environment": "production",
  "setupRequired": false
}
```

**Não retorna:**
- Caminhos de arquivos
- Hostname interno
- Senhas ou tokens
- Dados financeiros
- Informações de infraestrutura

## /api/system/capabilities

Endpoint público, sem autenticação.

```json
{
  "productName": "SCHF Core",
  "apiVersion": "0.1.0-SNAPSHOT",
  "features": [
    "authentication",
    "rbac",
    "suppliers",
    "categories",
    "financial-accounts",
    "payables",
    "payments",
    "reports",
    "audit",
    "migration-import",
    "tenant-isolation",
    "rate-limiting"
  ],
  "setupRequired": false
}
```

## /api/health

Endpoint público.

```json
{
  "status": "ok",
  "system": "SCHF",
  "version": "java-v2",
  "timestamp": "2026-07-12T18:00:00Z"
}
```

## Uso futuro

Estes endpoints serão consumidos por:

- PWA (descoberta de servidor)
- Cliente desktop Tauri
- App Kotlin Android
- Agente de instalação automatizada
