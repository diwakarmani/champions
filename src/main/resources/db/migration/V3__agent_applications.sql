-- Agent access requests and suspension (ADR-0004). Adds structure only; existing rows are unchanged.

ALTER TABLE public.users
    ADD COLUMN agent_suspended_at TIMESTAMP,
    ADD COLUMN agent_suspension_reason VARCHAR(1000),
    ADD COLUMN brokerage_name VARCHAR(150),
    ADD COLUMN license_number VARCHAR(50),
    ADD COLUMN license_state VARCHAR(2),
    ADD COLUMN years_experience INTEGER,
    ADD COLUMN service_areas VARCHAR(500),
    ADD COLUMN license_public BOOLEAN NOT NULL DEFAULT false;

-- Same rule as PropertyVisibility.isActiveAgent and PropertyRepository.ACTIVE_AGENT_OWNER.
CREATE OR REPLACE VIEW public.active_listing_agents AS
SELECT DISTINCT u.id AS user_id
FROM public.users u
JOIN public.user_roles ur ON ur.user_id = u.id
JOIN public.roles r ON r.id = ur.role_id
WHERE r.name = 'REALTOR'
  AND u.is_active = true
  AND u.deleted_at IS NULL
  AND u.agent_suspended_at IS NULL;

CREATE TABLE public.agent_applications (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    user_id BIGINT NOT NULL REFERENCES public.users (id),
    status VARCHAR(20) NOT NULL,
    license_number VARCHAR(50) NOT NULL,
    license_state VARCHAR(2) NOT NULL,
    brokerage_name VARCHAR(150) NOT NULL,
    years_experience INTEGER,
    service_areas VARCHAR(500),
    bio TEXT,
    photo_url VARCHAR(500),
    reviewed_by BIGINT REFERENCES public.users (id),
    reviewed_at TIMESTAMP,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT agent_applications_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT agent_applications_review_check CHECK (
        status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT agent_applications_reason_check CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- One open application per user, enforced by the database even when two requests race.
CREATE UNIQUE INDEX uq_agent_applications_one_pending ON public.agent_applications (user_id) WHERE status = 'PENDING';
CREATE INDEX idx_agent_applications_user ON public.agent_applications (user_id, created_at DESC);
CREATE INDEX idx_agent_applications_status ON public.agent_applications (status, created_at DESC);

CREATE TABLE public.agent_status_events (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES public.users (id),
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    performed_by BIGINT NOT NULL REFERENCES public.users (id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agent_status_events_action_check CHECK (action IN ('SUSPENDED', 'REINSTATED'))
);

CREATE INDEX idx_agent_status_events_agent ON public.agent_status_events (agent_id, created_at DESC);
