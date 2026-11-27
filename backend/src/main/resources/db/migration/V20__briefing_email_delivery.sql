CREATE TABLE personal_briefing_email_delivery (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    briefing_id UUID NOT NULL REFERENCES personal_briefing(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED','SENDING','SENT','FAILED','UNKNOWN','CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 1),
    started_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_personal_briefing_email_request UNIQUE (user_id,request_id),
    CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX idx_personal_briefing_email_due ON personal_briefing_email_delivery(scheduled_at)
    WHERE status='SCHEDULED';
CREATE INDEX idx_personal_briefing_email_owner ON personal_briefing_email_delivery(user_id,created_at DESC);
