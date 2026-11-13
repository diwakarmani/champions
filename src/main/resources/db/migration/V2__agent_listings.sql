-- Agent-only listings (ADR-0005). Schema only: production data changes are owner-run scripts (ADR-0015).

-- Some existing databases made this NOT NULL, unlike the entity. Guest inquiries need it nullable (ADR-0007).
ALTER TABLE public.inquiries ALTER COLUMN inquirer_id DROP NOT NULL;

-- Hibernate generated this list from the enum on some databases only. New notification types should not
-- need a migration, and the enum already limits what the application writes.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

-- The one definition of "who may have public listings" for native queries.
-- Same rule as PropertyVisibility.isActiveAgent and PropertyRepository.ACTIVE_AGENT_OWNER.
CREATE VIEW public.active_listing_agents AS
SELECT DISTINCT u.id AS user_id
FROM public.users u
JOIN public.user_roles ur ON ur.user_id = u.id
JOIN public.roles r ON r.id = ur.role_id
WHERE r.name = 'REALTOR'
  AND u.is_active = true
  AND u.deleted_at IS NULL;

CREATE TABLE public.listing_reassignments (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES public.properties (id),
    from_user_id BIGINT NOT NULL REFERENCES public.users (id),
    to_user_id BIGINT NOT NULL REFERENCES public.users (id),
    reassigned_by BIGINT NOT NULL REFERENCES public.users (id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_listing_reassignments_property ON public.listing_reassignments (property_id, created_at DESC);
