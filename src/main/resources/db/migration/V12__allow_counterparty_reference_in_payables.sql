ALTER TABLE payables ALTER COLUMN supplier_id DROP NOT NULL;
ALTER TABLE payables ADD COLUMN counterparty_id UUID REFERENCES unresolved_legacy_references(id);
CREATE INDEX idx_payables_counterparty_id ON payables (counterparty_id);
