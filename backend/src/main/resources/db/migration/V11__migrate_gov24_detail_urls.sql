UPDATE policy_source
SET source_url = 'https://plus.gov.kr/search/searchdtl/?srvcId='
        || external_id
        || '&typeSn=01'
WHERE source = 'GOV24'
  AND source_url LIKE 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/%';

UPDATE policy
SET source_url = 'https://plus.gov.kr/search/searchdtl/?srvcId='
        || SUBSTRING(external_id FROM 7)
        || '&typeSn=01'
WHERE external_id LIKE 'GOV24:%'
  AND source_url LIKE 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/%';

UPDATE policy
SET application_url = 'https://plus.gov.kr/search/searchdtl/?srvcId='
        || SUBSTRING(external_id FROM 7)
        || '&typeSn=01'
WHERE external_id LIKE 'GOV24:%'
  AND application_url LIKE 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/%';
