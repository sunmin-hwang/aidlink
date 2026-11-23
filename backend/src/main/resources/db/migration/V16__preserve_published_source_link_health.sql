-- Candidate inspection must not reset a known failure of the published URL.
ALTER TABLE policy ADD COLUMN source_link_published_usable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE policy SET source_link_published_usable = FALSE
WHERE source_link_checked_url = coalesce(verified_source_url, source_url)
    AND source_link_status NOT IN ('UNCHECKED', 'CONTENT_MATCH');
