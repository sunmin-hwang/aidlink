CREATE TABLE policy_rule_validation (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL UNIQUE
        REFERENCES policy_rule_candidate(id) ON DELETE CASCADE,
    score NUMERIC(5, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    report_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_rule_validation_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_policy_rule_validation_report CHECK (jsonb_typeof(report_json) = 'object')
);

CREATE INDEX idx_policy_rule_validation_status
    ON policy_rule_validation(status, created_at DESC);
