ALTER TABLE policy
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE policy
SET archived_at = updated_at
WHERE lifecycle_status = 'ARCHIVED';

ALTER TABLE policy
    ADD CONSTRAINT ck_policy_archived_at
        CHECK ((lifecycle_status = 'ARCHIVED') = (archived_at IS NOT NULL));

CREATE INDEX idx_policy_archived_at
    ON policy(archived_at)
    WHERE lifecycle_status = 'ARCHIVED';
