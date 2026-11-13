-- Owner-run, AFTER the S2 backend has started once, because that creates the new US types.
-- Moves listings from the old names to the US ones and switches the old names off (ADR-0014).
-- Safe to run twice. Nothing is deleted: rows stay, with is_active = false.
BEGIN;

CREATE TEMP TABLE us_type_map (old_name TEXT PRIMARY KEY, new_name TEXT NOT NULL) ON COMMIT DROP;
INSERT INTO us_type_map VALUES
    ('Independent House', 'Single Family Home'),
    ('Villa', 'Single Family Home'),
    ('Penthouse', 'Condo'),
    ('Studio Apartment', 'Apartment'),
    ('Plot', 'Land'),
    ('Agricultural Land', 'Land'),
    ('Commercial Space', 'Commercial'),
    ('Warehouse', 'Commercial');

-- Their sub-types belong to the old type, so they are cleared rather than left pointing across types.
UPDATE properties p
SET property_type_id = new_type.id,
    property_sub_type_id = NULL
FROM property_types old_type
JOIN us_type_map m ON m.old_name = old_type.name
JOIN property_types new_type ON new_type.name = m.new_name
WHERE p.property_type_id = old_type.id;

-- BHK sub-types under Apartment: bedrooms already holds this.
UPDATE properties
SET property_sub_type_id = NULL
WHERE property_sub_type_id IN (SELECT id FROM property_sub_types WHERE name ~* '(BHK|\mRK\M)');

UPDATE property_sub_types
SET is_active = false
WHERE name ~* '(BHK|\mRK\M)'
   OR property_type_id IN (
        SELECT t.id FROM property_types t JOIN us_type_map m ON m.old_name = t.name);

UPDATE property_types
SET is_active = false
WHERE name IN (SELECT old_name FROM us_type_map);

COMMIT;
