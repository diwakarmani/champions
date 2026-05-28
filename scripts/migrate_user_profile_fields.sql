-- Add occupation and website columns to users table
-- Run this on production (Render/Supabase) before deploying the new backend.

ALTER TABLE users ADD COLUMN IF NOT EXISTS occupation VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS website    VARCHAR(255);
