ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_verification_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_verification_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_email_verification_resolution CHECK (
        NOT (used_at IS NOT NULL AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_email_verification_used_at CHECK (
        used_at IS NULL OR used_at >= created_at
    ),
    CONSTRAINT ck_email_verification_revoked_at CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    )
);

CREATE TABLE policy_subscription (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    timezone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    urgent_deadline_alerts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    urgent_alert_consent_updated_at TIMESTAMPTZ,
    next_briefing_at TIMESTAMPTZ,
    subscribed_at TIMESTAMPTZ NOT NULL,
    paused_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_policy_subscription_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'CANCELLED')
    ),
    CONSTRAINT ck_policy_subscription_paused CHECK (
        (status = 'PAUSED') = (paused_at IS NOT NULL)
    ),
    CONSTRAINT ck_policy_subscription_cancelled CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_policy_subscription_next_briefing CHECK (
        status = 'ACTIVE' OR next_briefing_at IS NULL
    ),
    CONSTRAINT ck_policy_subscription_urgent_consent CHECK (
        NOT urgent_deadline_alerts_enabled OR urgent_alert_consent_updated_at IS NOT NULL
    )
);

CREATE TABLE subscription_schedule (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES policy_subscription(id) ON DELETE CASCADE,
    slot_number SMALLINT NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    delivery_time TIME WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_subscription_schedule_slot UNIQUE (subscription_id, slot_number),
    CONSTRAINT uq_subscription_schedule_time UNIQUE (
        subscription_id,
        day_of_week,
        delivery_time
    ),
    CONSTRAINT ck_subscription_schedule_slot CHECK (slot_number BETWEEN 1 AND 2),
    CONSTRAINT ck_subscription_schedule_day CHECK (
        day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )
    )
);

CREATE TABLE subscription_policy_match (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES policy_subscription(id) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    current_status VARCHAR(40) NOT NULL,
    previous_status VARCHAR(40),
    result_payload JSONB NOT NULL,
    policy_source_updated_at TIMESTAMPTZ,
    first_observed_at TIMESTAMPTZ NOT NULL,
    last_evaluated_at TIMESTAMPTZ NOT NULL,
    last_changed_at TIMESTAMPTZ NOT NULL,
    last_notified_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscription_policy_match UNIQUE (subscription_id, policy_id),
    CONSTRAINT ck_subscription_policy_match_current CHECK (
        current_status IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'UNKNOWN', 'NEAR_MISS')
    ),
    CONSTRAINT ck_subscription_policy_match_previous CHECK (
        previous_status IS NULL OR previous_status IN (
            'ELIGIBLE', 'NOT_ELIGIBLE', 'UNKNOWN', 'NEAR_MISS'
        )
    ),
    CONSTRAINT ck_subscription_policy_match_times CHECK (
        first_observed_at <= last_changed_at
        AND last_changed_at <= last_evaluated_at
        AND (last_notified_at IS NULL OR first_observed_at <= last_notified_at)
    )
);

CREATE TABLE policy_briefing_delivery (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES policy_subscription(id) ON DELETE CASCADE,
    idempotency_key CHAR(64) NOT NULL UNIQUE,
    recipient_email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at TIMESTAMPTZ NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    report_payload JSONB,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    provider_message_id VARCHAR(255),
    failure_reason TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_policy_briefing_status CHECK (
        status IN ('SCHEDULED', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_policy_briefing_period CHECK (period_start < period_end),
    CONSTRAINT ck_policy_briefing_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_policy_briefing_sent_at CHECK (
        status <> 'SENT' OR sent_at IS NOT NULL
    )
);

CREATE INDEX idx_email_verification_active
    ON email_verification_token(user_id, expires_at DESC)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_policy_subscription_due
    ON policy_subscription(next_briefing_at)
    WHERE status = 'ACTIVE' AND next_briefing_at IS NOT NULL;

CREATE INDEX idx_subscription_schedule_subscription
    ON subscription_schedule(subscription_id, slot_number);

CREATE INDEX idx_subscription_policy_match_changed
    ON subscription_policy_match(subscription_id, last_changed_at DESC);

CREATE INDEX idx_subscription_policy_match_policy
    ON subscription_policy_match(policy_id);

CREATE INDEX idx_policy_briefing_delivery_due
    ON policy_briefing_delivery(status, scheduled_at)
    WHERE status IN ('SCHEDULED', 'FAILED');

CREATE INDEX idx_policy_briefing_delivery_subscription
    ON policy_briefing_delivery(subscription_id, created_at DESC);
