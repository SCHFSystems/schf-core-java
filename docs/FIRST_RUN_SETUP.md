# First-Run Setup

## Comportamento

O SCHF Clean nunca vem com usuário padrão, senha padrão, organização real, dados financeiros, DevSeed ativo ou tokens predefinidos.

O setup de primeira execução segue este fluxo:

1. PostgreSQL sobe limpo.
2. Flyway V1-V6 é aplicado automaticamente.
3. A tabela `instance_setup` contém uma única linha com `completed = FALSE`.
4. O endpoint `GET /api/setup/status` retorna `{ "setupRequired": true }`.
5. O administrador chama `POST /api/setup/initialize` com dados da organização e admin.
6. O sistema cria:
   - Organização
   - Permissões (16 permissões padrão)
   - Role OWNER com todas as permissões
   - Usuário administrador com role OWNER (mustChangePassword = true)
   - Marca `instance_setup.completed = TRUE`
   - Registra audit log `INSTANCE_SETUP_COMPLETED`
7. `GET /api/setup/status` agora retorna `{ "setupRequired": false }`.
8. `POST /api/setup/initialize` retorna 400 com "Setup has already been completed".

## API

### GET /api/setup/status

Público. Retorna se o setup é necessário.

```json
{ "setupRequired": true }
```

### POST /api/setup/initialize

Público (disponível apenas antes do setup). Cria organização e admin.

```json
{
  "organizationCode": "MINHA_ORG",
  "organizationName": "Minha Organizacao",
  "adminUsername": "admin",
  "adminEmail": "admin@example.com",
  "adminPassword": "change_me_local_dev_placeholder_only" # gitleaks:allow
}
```

Resposta (201 Created):

```json
{
  "completed": true,
  "organizationId": "uuid-da-organizacao",
  "message": "Instance setup completed successfully"
}
```

## Concorrência

O setup usa `PESSIMISTIC_WRITE` na linha `instance_setup` dentro de uma transação. Apenas o primeiro request que adquirir o lock conseguirá criar a organização. Requests concorrentes receberão 400 "Setup has already been completed".

## Decisão arquitetural

**Opção escolhida:** endpoint público sem token de bootstrap.

**Justificativa:**
- O proxy Caddy já protege a rede externa por padrão.
- O setup só funciona enquanto `instance_setup.completed = FALSE`, o que é uma janela extremamente curta.
- Adicionar token de bootstrap introduziria complexidade de distribuição e armazenamento sem benefício real contra o risco mitigado.
- Para ambientes que exigem segurança adicional na janela de setup, recomenda-se:
  - Firewall de rede bloqueando `/api/setup/` até o momento da configuração.
  - Ou execução do setup via `docker compose exec api curl ...` local.

**Opção rejeitada:** token de bootstrap armazenado em arquivo.
**Motivo:** o token precisaria ser gerado, armazenado e distribuído — mesma superfície de ataque que expor o endpoint, com complexidade maior.

## DevSeed

O `DevSeed` (`@Profile("dev")`) continua existindo para desenvolvimento local, mas **não é ativado** no ambiente clean (profile `production` é o padrão).
