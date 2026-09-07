-- Intentionally retain MEDIUMTEXT on rollback.
-- Shrinking to TEXT could truncate existing logs; older code accepts MEDIUMTEXT.
SELECT 1;
