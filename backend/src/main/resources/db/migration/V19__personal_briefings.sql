-- Test entitlement is deliberately separate from payment and email subscriptions.
CREATE TABLE briefing_test_access (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL,
    updated_by VARCHAR(320) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE personal_briefing (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    profile_snapshot JSONB NOT NULL,
    report_payload JSONB,
    failure_message TEXT,
    CHECK ((status = 'COMPLETED' AND report_payload IS NOT NULL AND failure_message IS NULL)
        OR (status = 'FAILED' AND report_payload IS NULL AND failure_message IS NOT NULL))
);
CREATE INDEX idx_personal_briefing_owner ON personal_briefing(user_id, created_at DESC);
