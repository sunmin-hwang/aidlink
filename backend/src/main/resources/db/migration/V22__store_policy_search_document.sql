-- Preserve the original simple configuration, field order, separators and positions.
-- A stored generated column is refreshed for every writer, including collection and review.
-- This rewrites the table and takes a strong lock: run in the deployment maintenance window.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE policy ADD COLUMN search_document tsvector
    GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(name, '') || ' ' || coalesce(description, '') || ' '
                || coalesce(target_description, '') || ' ' || coalesce(support_content, '') || ' '
                || coalesce(provider, '')
        )
    ) STORED;

CREATE INDEX idx_policy_search_vector ON policy USING GIN (search_document);
DROP INDEX idx_policy_search_document;
ANALYZE policy;
