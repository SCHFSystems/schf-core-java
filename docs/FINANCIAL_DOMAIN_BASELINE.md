# Financial Domain Baseline

SCHF v2 financial domain baseline: entities, migrations, APIs, and tests.

## Entities

| Entity | Table | Key Fields |
|--------|-------|------------|
| Supplier | suppliers | id, organization_id, name, document, email, phone, active |
| Category | categories | id, organization_id, name, type (EXPENSE/REVENUE), active |
| FinancialAccount | financial_accounts | id, organization_id, name, type (CASH/BANK/CREDIT_CARD/OTHER), bank_name, agency, account_number, active |
| Payable | payables | id, organization_id, supplier_id, category_id, financial_account_id, description, document_number, issue_date, due_date, amount (NUMERIC(19,4)), status (OPEN/PAID/CANCELED/OVERDUE) |
| Payment | payments | id, organization_id, payable_id, financial_account_id, payment_date, amount (NUMERIC(19,4)), notes |

## Migrations

- V2__financial_domain_baseline.sql: creates tables and indexes for all 5 entities.

## APIs

| Endpoint | Method | Description |
|----------|--------|-------------|
| /api/suppliers | GET | List suppliers for current organization |
| /api/suppliers | POST | Create supplier |
| /api/categories | GET | List categories |
| /api/categories | POST | Create category |
| /api/financial-accounts | GET | List accounts |
| /api/financial-accounts | POST | Create account |
| /api/payables | GET | List payables |
| /api/payables | POST | Create payable |
| /api/payables/{id}/payments | POST | Register payment for payable |

## Rules

- Money handled with `BigDecimal` (NUMERIC(19,4) in DB).
- Partial payments allowed; payable transitions to PAID when fully paid.
- Over-payment rejected with `IllegalArgumentException`.
- Canceled payables cannot be paid.
- Organization isolation via `TenantContext` (auto-loads first org in dev, configurable via header in prod).
- Controllers use `@Valid` for request validation.
- No business logic in controllers; services own rules.

## Tests

- Unit: `PaymentServiceUnitTest.java` (money precision, status transitions).
- Integration: `PayablePaymentIntegrationTest.java` (Testcontainers, partial/full/overpayment scenarios).
- Architecture: `ArchitectureRulesTest.java` (finance packages must not depend on Spring Web).
- Flyway: migration tested via integration test.
- API: smoke tests via integration test.