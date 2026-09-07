-- Intentionally retain VARCHAR(256) on rollback.
-- Shrinking to VARCHAR(32) could truncate application identifiers.
-- Older application versions accept the larger column.
SELECT 1;
