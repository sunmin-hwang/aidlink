-- Keep recurring hints separate from confirmed dates. Java ApplicationAvailability mirrors these rules.
CREATE FUNCTION policy_application_guidance_open(raw TEXT, on_date DATE)
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$
    WITH normalized AS (
        SELECT regexp_replace(coalesce(raw, ''), '[[:space:]]+', '', 'g') AS value
    ), parsed AS (
        SELECT value,
            regexp_match(value, '^매년(?:도)?([0-9]{1,2})월(?:중|신청)?$') AS single_month,
            regexp_match(value, '^매년(?:도)?([0-9]{1,2})월?[~∼～-]([0-9]{1,2})월(?:중|신청)?$') AS month_range
        FROM normalized
    ), months AS (
        SELECT value, coalesce(single_month[1], month_range[1])::int AS first_month,
            coalesce(single_month[1], month_range[2])::int AS last_month
        FROM parsed
    )
    SELECT value IN ('별도의신청절차가없음', '별도신청절차없음', '개인신청절차없음', '신청불필요', '신청불요')
        OR coalesce(first_month BETWEEN 1 AND 12 AND last_month BETWEEN 1 AND 12
            AND CASE WHEN first_month <= last_month
                THEN extract(month FROM on_date) BETWEEN first_month AND last_month
                ELSE extract(month FROM on_date) >= first_month OR extract(month FROM on_date) <= last_month
            END, false)
    FROM months;
$$;

CREATE FUNCTION policy_application_available_at(kind VARCHAR, starts DATE, ends DATE,
    starts_time TIME, ends_time TIME, raw TEXT, at_time TIMESTAMP)
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$
    SELECT CASE WHEN kind = 'UNKNOWN' THEN policy_application_guidance_open(raw, at_time::date)
        ELSE kind IN ('FIXED', 'YEAR_ROUND')
            AND (starts IS NULL OR starts < at_time::date
                OR (starts = at_time::date AND (starts_time IS NULL OR starts_time <= at_time::time)))
            AND (ends IS NULL OR ends > at_time::date
                OR (ends = at_time::date AND (ends_time IS NULL OR ends_time >= at_time::time)))
        END;
$$;

CREATE FUNCTION policy_application_available_now(kind VARCHAR, starts DATE, ends DATE,
    starts_time TIME, ends_time TIME, raw TEXT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT policy_application_available_at(kind, starts, ends, starts_time, ends_time, raw,
        (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::timestamp);
$$;
