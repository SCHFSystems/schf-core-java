CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(160) NOT NULL,
    document VARCHAR(40),
    email VARCHAR(180),
    phone VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE financial_accounts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    bank_name VARCHAR(120),
    agency VARCHAR(40),
    account_number VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payables (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    supplier_id UUID NOT NULL REFERENCES suppliers (id),
    category_id UUID NOT NULL REFERENCES categories (id),
    financial_account_id UUID REFERENCES financial_accounts (id),
    description VARCHAR(255) NOT NULL,
    document_number VARCHAR(80),
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    payable_id UUID NOT NULL REFERENCES payables (id),
    financial_account_id UUID NOT NULL REFERENCES financial_accounts (id),
    payment_date DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_suppliers_organization_id ON suppliers (organization_id);
CREATE INDEX idx_categories_organization_id ON categories (organization_id);
CREATE INDEX idx_categories_type ON categories (type);
CREATE INDEX idx_financial_accounts_organization_id ON financial_accounts (organization_id);
CREATE INDEX idx_financial_accounts_type ON financial_accounts (type);
CREATE INDEX idx_payables_organization_id ON payables (organization_id);
CREATE INDEX idx_payables_supplier_id ON payables (supplier_id);
CREATE INDEX idx_payables_category_id ON payables (category_id);
CREATE INDEX idx_payables_status ON payables (status);
CREATE INDEX idx_payables_due_date ON payables (due_date);
CREATE INDEX idx_payments_organization_id ON payments (organization_id);
CREATE INDEX idx_payments_payable_id ON payments (payable_id);