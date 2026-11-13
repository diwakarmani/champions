-- Owner-run, after reading RUNBOOK_S2. Safe to run twice. Adds access; removes nothing (ADR-0003).
-- Former sellers can still browse, save homes and contact agents as buyers.
BEGIN;

INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, buyer.id
FROM user_roles ur
JOIN roles seller ON seller.id = ur.role_id AND seller.name = 'SELLER'
CROSS JOIN (SELECT id FROM roles WHERE name = 'BUYER') buyer
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = ur.user_id AND existing.role_id = buyer.id
);

COMMIT;
