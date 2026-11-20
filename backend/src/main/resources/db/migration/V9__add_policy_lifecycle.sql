ALTER TABLE policy
    ADD COLUMN lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD COLUMN missed_collection_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_policy_lifecycle_status
        CHECK (lifecycle_status IN ('ACTIVE', 'EXPIRED', 'SOURCE_REMOVED', 'ARCHIVED')),
    ADD CONSTRAINT ck_policy_missed_collection_count
        CHECK (missed_collection_count >= 0);

UPDATE policy p
SET last_seen_at = COALESCE(
        (
            SELECT MAX(ps.last_collected_at)
            FROM policy_source ps
            WHERE ps.policy_id = p.id
        ),
        p.updated_at
    );

UPDATE policy
SET lifecycle_status = 'ARCHIVED'
WHERE external_id LIKE 'SAMPLE-%';

UPDATE policy
SET lifecycle_status = 'EXPIRED'
WHERE lifecycle_status = 'ACTIVE'
  AND application_end IS NOT NULL
  AND application_end < CURRENT_DATE;

CREATE INDEX idx_policy_lifecycle_status ON policy(lifecycle_status, id);
CREATE INDEX idx_policy_last_seen_at ON policy(last_seen_at);
