CREATE TABLE policy_rule_candidate (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    source_text_hash CHAR(64) NOT NULL,
    candidate_json JSONB NOT NULL,
    parser_model VARCHAR(120) NOT NULL,
    response_id VARCHAR(160),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_rule_candidate_json_object
        CHECK (jsonb_typeof(candidate_json) = 'object'),
    CONSTRAINT ck_policy_rule_candidate_tokens
        CHECK (input_tokens >= 0 AND output_tokens >= 0)
);

CREATE INDEX idx_policy_rule_candidate_latest
    ON policy_rule_candidate(policy_id, created_at DESC);
