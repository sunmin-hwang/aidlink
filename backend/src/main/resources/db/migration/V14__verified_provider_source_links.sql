-- Keep human-verified provider pages separate from stale API source URLs.
ALTER TABLE policy ADD COLUMN verified_source_url TEXT;
ALTER TABLE policy ADD COLUMN source_link_verified_at TIMESTAMPTZ;

UPDATE policy
SET verified_source_url = CASE external_id
    WHEN 'GOV24:O00148800003' THEN 'https://dle.study.daegu.kr/portal/posts/BBS_00029/detail/29769'
    WHEN 'GOV24:O00108600005' THEN 'https://gjmc.or.kr/content/94'
    WHEN 'GOV24:O00108600004' THEN 'https://gjmc.or.kr/content/170'
END,
source_link_verified_at = CURRENT_TIMESTAMP
WHERE external_id IN ('GOV24:O00148800003', 'GOV24:O00108600005', 'GOV24:O00108600004');
