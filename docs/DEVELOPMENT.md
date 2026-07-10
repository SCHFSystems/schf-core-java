# Development

## Package root
`br.com.schf`

## Initial packages
- `app`
- `config`
- `security`
- `shared`
- `audit`
- `organization`
- `user`
- `finance`
- `supplier`
- `account`
- `payable`
- `category`
- `report`
- `migration`

## Architecture guardrails
- Domain packages must not depend on Spring Web.
- Database changes must go through Flyway.
- Security defaults must not expose non-health endpoints.
- Tests must not use real data.

## Commands
```powershell
mvn verify
docker compose config
docker compose up -d --build
curl http://localhost:8088/actuator/health
docker compose down
```
