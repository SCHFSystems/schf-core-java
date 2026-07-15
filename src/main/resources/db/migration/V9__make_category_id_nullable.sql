-- Make category_id nullable in payables table
-- Legacy payables do not have category assignments
ALTER TABLE payables ALTER COLUMN category_id DROP NOT NULL;
