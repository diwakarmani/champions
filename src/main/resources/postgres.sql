CREATE EXTENSION IF NOT EXISTS postgis;

-- GEO column
ALTER TABLE properties
ADD COLUMN IF NOT EXISTS location GEOGRAPHY(Point, 4326);

CREATE INDEX IF NOT EXISTS idx_properties_location
ON properties USING GIST(location);

-- Interaction tracking
CREATE TABLE IF NOT EXISTS user_property_interactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    property_id BIGINT NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_interactions_user ON user_property_interactions(user_id);
CREATE INDEX idx_interactions_property ON user_property_interactions(property_id);
CREATE INDEX idx_interactions_created ON user_property_interactions(created_at);

-- Discovery cache (precomputed)
CREATE TABLE IF NOT EXISTS discovery_cache (
    user_id BIGINT,
    category VARCHAR(20) NOT NULL,
    city VARCHAR(100),
    property_id BIGINT NOT NULL,
    score DOUBLE PRECISION,
    rank INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, category, property_id, city)
);

CREATE INDEX idx_discovery_lookup
ON discovery_cache(user_id, category, city);
