-- US amenity wording (ADR-0014 Amendment 2, S12). Nothing is deleted: rows are renamed or hidden
-- (is_active = false), so listings that use any of them keep their links and the change can be undone.
-- Client to confirm the final list.

-- 1. Rows left behind by test runs against a shared database (category 'Test').
UPDATE public.property_amenities SET is_active = false WHERE category = 'Test';

-- 2. India-market items with no US equivalent buyers search for.
UPDATE public.property_amenities SET is_active = false
 WHERE name IN ('Piped Gas', 'Gas Pipeline', 'Modular Kitchen', 'Water Supply', 'Sewage Treatment');

-- 3. US wording. Skipped when the new name already exists (names are unique).
UPDATE public.property_amenities a SET name = r.new_name
  FROM (VALUES
        ('Gymnasium', 'Fitness Center'),
        ('Children''s Play Area', 'Playground'),
        ('Sports Facility', 'Sports Court'),
        ('Lift', 'Elevator'),
        ('Power Backup', 'Backup Generator'),
        ('Waste Disposal', 'Trash Pickup'),
        ('Maintenance Staff', 'On-site Maintenance'),
        ('Laundry Service', 'On-site Laundry'),
        ('Air Conditioning', 'Central Air'),
        ('Internet/WiFi', 'High-Speed Internet'),
        ('Shopping Complex', 'Near Shopping'),
        ('School/College', 'Near Schools'),
        ('Hospital', 'Near Hospital')
       ) AS r(old_name, new_name)
 WHERE a.name = r.old_name
   AND NOT EXISTS (SELECT 1 FROM public.property_amenities b WHERE b.name = r.new_name);
