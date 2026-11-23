-- Collection updates only API fields; review and check metadata remain independent.
ALTER TABLE policy
    ADD COLUMN source_link_checked_url TEXT,
    ADD COLUMN source_link_status VARCHAR(40) NOT NULL DEFAULT 'UNCHECKED',
    ADD COLUMN source_link_checked_at TIMESTAMPTZ,
    ADD COLUMN source_link_final_url TEXT,
    ADD COLUMN source_link_http_status INTEGER,
    ADD COLUMN source_link_reason TEXT,
    ADD COLUMN source_link_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN source_link_next_check_at TIMESTAMPTZ,
    ADD COLUMN source_link_reviewed_by VARCHAR(320),
    ADD COLUMN source_link_review_evidence TEXT;

CREATE INDEX idx_policy_source_link_due
    ON policy (source_link_next_check_at, id) WHERE lifecycle_status = 'ACTIVE';
