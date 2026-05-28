-- Fix property images: replace non-property/human photos with type-appropriate
-- USA residential property images from Unsplash (no people, exterior shots).
-- Run this on both local and production (Render/Supabase) databases.
--
-- Apartment  : modern urban apartment buildings
-- Villa      : luxury homes with grounds/pool
-- Independent House : suburban American homes
-- Penthouse  : premium luxury homes

UPDATE property_images pi
SET
  image_url = CASE
    WHEN pt.name = 'Apartment' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1560448075-bbc5d02ced7f?w=800&q=80&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1486325212027-8081e485255e?w=800&q=80&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800&q=80&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1574362848787-cf84af83eecb?w=800&q=80&auto=format&fit=crop'
      END
    WHEN pt.name = 'Villa' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800&q=80&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1613977257363-707ba9578fac?w=800&q=80&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&q=80&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1605276374104-dee2a0ed3cd6?w=800&q=80&auto=format&fit=crop'
      END
    WHEN pt.name = 'Penthouse' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=800&q=80&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1605276374104-dee2a0ed3cd6?w=800&q=80&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&q=80&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=800&q=80&auto=format&fit=crop'
      END
    ELSE  -- Independent House + any other type
      CASE ((pi.property_id + pi.display_order) % 6)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=800&q=80&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1570129477492-45c003edd2be?w=800&q=80&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800&q=80&auto=format&fit=crop'
        WHEN 3 THEN 'https://images.unsplash.com/photo-1564013799919-ab3e7c4e17b0?w=800&q=80&auto=format&fit=crop'
        WHEN 4 THEN 'https://images.unsplash.com/photo-1580587771525-4c77d4c27e3e?w=800&q=80&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?w=800&q=80&auto=format&fit=crop'
      END
  END,
  thumbnail_url = CASE
    WHEN pt.name = 'Apartment' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1560448075-bbc5d02ced7f?w=400&q=70&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1486325212027-8081e485255e?w=400&q=70&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=400&q=70&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1574362848787-cf84af83eecb?w=400&q=70&auto=format&fit=crop'
      END
    WHEN pt.name = 'Villa' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=400&q=70&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1613977257363-707ba9578fac?w=400&q=70&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=400&q=70&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1605276374104-dee2a0ed3cd6?w=400&q=70&auto=format&fit=crop'
      END
    WHEN pt.name = 'Penthouse' THEN
      CASE ((pi.property_id + pi.display_order) % 4)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=400&q=70&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1605276374104-dee2a0ed3cd6?w=400&q=70&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=400&q=70&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=400&q=70&auto=format&fit=crop'
      END
    ELSE
      CASE ((pi.property_id + pi.display_order) % 6)
        WHEN 0 THEN 'https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=400&q=70&auto=format&fit=crop'
        WHEN 1 THEN 'https://images.unsplash.com/photo-1570129477492-45c003edd2be?w=400&q=70&auto=format&fit=crop'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=400&q=70&auto=format&fit=crop'
        WHEN 3 THEN 'https://images.unsplash.com/photo-1564013799919-ab3e7c4e17b0?w=400&q=70&auto=format&fit=crop'
        WHEN 4 THEN 'https://images.unsplash.com/photo-1580587771525-4c77d4c27e3e?w=400&q=70&auto=format&fit=crop'
        ELSE       'https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?w=400&q=70&auto=format&fit=crop'
      END
  END
FROM properties p
JOIN property_types pt ON p.property_type_id = pt.id
WHERE pi.property_id = p.id;
