ALTER TABLE policy
    ADD COLUMN application_period_raw TEXT NOT NULL DEFAULT '',
    ADD COLUMN application_period_kind VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN source_application_start DATE,
    ADD COLUMN source_application_end DATE,
    ADD COLUMN source_application_start_time TIME,
    ADD COLUMN source_application_end_time TIME,
    ADD COLUMN application_start_time TIME,
    ADD COLUMN application_end_time TIME,
    ADD COLUMN verified_application_period_text TEXT,
    ADD COLUMN verified_application_start DATE,
    ADD COLUMN verified_application_end DATE,
    ADD COLUMN verified_application_start_time TIME,
    ADD COLUMN verified_application_end_time TIME,
    ADD COLUMN application_period_verified_url TEXT,
    ADD COLUMN application_period_verified_at TIMESTAMPTZ,
    ADD COLUMN application_period_reviewed_by VARCHAR(320),
    ADD COLUMN application_period_review_evidence TEXT,
    ADD COLUMN application_period_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN application_period_review_history JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Existing structured dates remain usable. Textual dates are applied only after an admin dry-run.
UPDATE policy SET application_period_kind = 'FIXED',
    source_application_start = application_start, source_application_end = application_end
WHERE application_start IS NOT NULL OR application_end IS NOT NULL;

CREATE FUNCTION policy_application_open_now(kind VARCHAR, starts DATE, ends DATE, starts_time TIME, ends_time TIME)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT kind IN ('FIXED', 'YEAR_ROUND')
      AND (starts IS NULL OR starts < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
        OR (starts = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
          AND (starts_time IS NULL OR starts_time <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::time)))
      AND (ends IS NULL OR ends > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
        OR (ends = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
          AND (ends_time IS NULL OR ends_time >= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::time)));
$$;
