# Backup and Restore

## Backup

### Funcionamento

O script `deploy/schf-clean/backup.ps1` executa:

1. `pg_dump` com compressão gzip (nível 9) dentro do container PostgreSQL.
2. Gera manifesto JSON com timestamp, instanceId, versão do SCHF, database e SHA-256.
3. Armazena em `D:\SCHF\_runtime\schf-clean\backups\`.

### Formato do arquivo

```
D:\SCHF\_runtime\schf-clean\backups\
  schf-clean-20260712-183000.sql.gz
  schf-clean-20260712-183000.manifest.json
```

### Manifesto

```json
{
  "backupFile": "schf-clean-20260712-183000.sql.gz",
  "timestamp": "2026-07-12T18:30:00.000Z",
  "instanceId": "uuid",
  "schfVersion": "0.1.0-SNAPSHOT",
  "database": "schf_v2",
  "sha256": "abcdef...",
  "status": "completed"
}
```

### Uso manual

```powershell
.\deploy\schf-clean\backup.ps1
```

A senha do banco é lida automaticamente do `.env`.

## Restore

### Funcionamento

O script `deploy/schf-clean/restore.ps1`:

1. Lista backups disponíveis.
2. Valida checksum SHA-256 contra o manifesto (se existir).
3. Exige confirmação explícita `"yes"`.
4. Para serviços `api` e `proxy`.
5. Droppa e recria o banco de dados.
6. Restaura com `gunzip | psql`.
7. Reinicia serviços.
8. Aguarda healthcheck da API.

### Uso manual

```powershell
.\deploy\schf-clean\restore.ps1
```

### Restrições

- O restore nunca remove `.env`, volumes ou backups.
- A senha nunca aparece em logs ou nomes de processo.
- Após o restore, o setup não é resetado (a tabela `instance_setup` é preservada no dump).

## Recomendações

- Executar backup antes de qualquer atualização.
- Manter backups por no mínimo 7 dias.
- Testar restore periodicamente.
- Backups contêm dados do cliente — tratar como confidenciais.
