-- ============================================================
-- Local dev DB cleanup — removes test/garbage data
-- Run once on your local DB before building the distribution JAR
-- Safe to run multiple times (DELETE WHERE is idempotent)
-- ============================================================

-- 1. Remove test/garbage users (keep only the four canonical dev accounts
--    plus the seeded admin; all others are test noise)
DELETE FROM user_roles
WHERE user_id IN (
    SELECT id FROM users
    WHERE email NOT IN (
        'admin@propertyapp.com',
        'buyer@propertyapp.com',
        'seller@propertyapp.com',
        'realtor@propertyapp.com'
    )
);

-- Clear group data entirely (REALTOR_GROUP_ADMIN feature removed)
DELETE FROM group_memberships;
DELETE FROM realtor_groups;

-- Clear all data referencing garbage users (order matters: children before parent)
DELETE FROM user_favorites      WHERE user_id    IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM user_addresses      WHERE user_id    IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM refresh_tokens      WHERE user_id    IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM notifications       WHERE recipient_id IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM notification_tokens WHERE user_id    IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM audit_logs          WHERE actor_user_id IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));
DELETE FROM otp_tokens          WHERE identifier NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com');
DELETE FROM verification_tokens WHERE user_id    IN (SELECT id FROM users WHERE email NOT IN ('admin@propertyapp.com','buyer@propertyapp.com','seller@propertyapp.com','realtor@propertyapp.com'));

DELETE FROM users
WHERE email NOT IN (
    'admin@propertyapp.com',
    'buyer@propertyapp.com',
    'seller@propertyapp.com',
    'realtor@propertyapp.com'
);

-- 2. Remove test/garbage properties (keep only the 45 seeded sample ones with
--    real US city names; remove anything clearly labelled as test, draft junk,
--    Indian city references, or placeholder names)
DELETE FROM property_images         WHERE property_id IN (SELECT id FROM properties WHERE id > 45);
DELETE FROM property_amenity_mapping WHERE property_id IN (SELECT id FROM properties WHERE id > 45);
DELETE FROM user_property_interactions WHERE property_id IN (SELECT id FROM properties WHERE id > 45);
DELETE FROM discovery_cache         WHERE property_id IN (SELECT id FROM properties WHERE id > 45);
DELETE FROM user_favorites          WHERE property_id IN (SELECT id FROM properties WHERE id > 45);
DELETE FROM inquiries               WHERE property_id IN (SELECT id FROM properties WHERE id > 45);

DELETE FROM properties WHERE id > 45;

-- 3. Remove the deprecated REALTOR_GROUP_ADMIN role (feature removed)
DELETE FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = 'REALTOR_GROUP_ADMIN');
DELETE FROM roles WHERE name = 'REALTOR_GROUP_ADMIN';

-- 4. Clean up expired/stale auth tokens
DELETE FROM otp_tokens WHERE expires_at < NOW();
DELETE FROM refresh_tokens WHERE expires_at < NOW();
DELETE FROM verification_tokens WHERE expires_at < NOW();

-- 5. Reset sequences so new IDs continue cleanly after the kept rows
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('properties_id_seq', (SELECT MAX(id) FROM properties));

-- Done
SELECT 'Cleanup complete' AS status,
       (SELECT COUNT(*) FROM users) AS users_remaining,
       (SELECT COUNT(*) FROM properties) AS properties_remaining,
       (SELECT COUNT(*) FROM roles) AS roles_remaining;
