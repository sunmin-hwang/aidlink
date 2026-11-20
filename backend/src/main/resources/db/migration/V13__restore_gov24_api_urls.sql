-- Preserve the API's URL and query parameters instead of guessing portal routes.
WITH latest AS (
    SELECT DISTINCT ON (external_id) external_id, raw_payload
    FROM raw_policy
    WHERE source = 'GOV24'
    ORDER BY external_id, collected_at DESC, id DESC
), urls AS (
    SELECT external_id,
        COALESCE(NULLIF(BTRIM(raw_payload->>'상세조회URL'), ''),
                 NULLIF(BTRIM(raw_payload->>'상세URL'), ''),
                 NULLIF(BTRIM(raw_payload->>'sourceUrl'), ''), '') AS source_url,
        COALESCE(NULLIF(BTRIM(raw_payload->>'온라인신청사이트URL'), ''),
                 NULLIF(BTRIM(raw_payload->>'온라인신청URL'), ''),
                 NULLIF(BTRIM(raw_payload->>'applicationUrl'), '')) AS application_url
    FROM latest
)
UPDATE policy p
SET source_url = u.source_url,
    application_url = COALESCE(u.application_url, u.source_url)
FROM urls u
WHERE p.external_id = 'GOV24:' || u.external_id;

UPDATE policy_source ps
SET source_url = p.source_url
FROM policy p
WHERE ps.policy_id = p.id AND ps.source = 'GOV24';
