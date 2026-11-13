-- Guest contact (ADR-0007). Adds structure only; existing rows are unchanged.

-- A guest who reveals an agent's phone and email has no account.
ALTER TABLE public.property_contact_events ALTER COLUMN user_id DROP NOT NULL;

-- Dedupe check: the same email on the same listing within 24 hours.
CREATE INDEX idx_inquiries_property_email_created
    ON public.inquiries (property_id, lower(email), created_at DESC);
