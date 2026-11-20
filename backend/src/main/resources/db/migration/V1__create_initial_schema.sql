CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE policy (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(160) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    provider VARCHAR(200) NOT NULL,
    category VARCHAR(80) NOT NULL,
    region_code VARCHAR(40) NOT NULL,
    description TEXT NOT NULL,
    target_description TEXT NOT NULL,
    support_content TEXT NOT NULL,
    application_start DATE,
    application_end DATE,
    application_method TEXT NOT NULL,
    application_url TEXT NOT NULL,
    source_url TEXT NOT NULL,
    analysis_status VARCHAR(40) NOT NULL,
    source_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_application_period
        CHECK (application_start IS NULL OR application_end IS NULL OR application_start <= application_end)
);

CREATE TABLE raw_policy (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(80) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    raw_payload JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_raw_policy_source_hash UNIQUE (source, external_id, content_hash)
);

CREATE TABLE policy_source (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    source VARCHAR(80) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    source_url TEXT NOT NULL,
    last_collected_at TIMESTAMPTZ,
    CONSTRAINT uq_policy_source UNIQUE (source, external_id)
);

CREATE TABLE policy_rule (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL UNIQUE REFERENCES policy(id) ON DELETE CASCADE,
    rule_json JSONB NOT NULL,
    validation_score NUMERIC(5, 2),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_rule_json_object CHECK (jsonb_typeof(rule_json) = 'object'),
    CONSTRAINT ck_policy_rule_score CHECK (validation_score IS NULL OR validation_score BETWEEN 0 AND 100)
);

CREATE TABLE policy_rule_version (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    rule_json JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    CONSTRAINT uq_policy_rule_version UNIQUE (policy_id, version),
    CONSTRAINT ck_policy_rule_version_period CHECK (effective_to IS NULL OR effective_from <= effective_to)
);

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    birth_date DATE,
    region_code VARCHAR(40),
    employment_status VARCHAR(40),
    student_status VARCHAR(40),
    personal_monthly_income NUMERIC(15, 2),
    household_monthly_income NUMERIC(15, 2),
    median_income_ratio NUMERIC(7, 2),
    weekly_work_hours INTEGER,
    household_size INTEGER,
    household_type VARCHAR(40),
    housing_type VARCHAR(40),
    home_ownership VARCHAR(40),
    asset NUMERIC(18, 2),
    marital_status VARCHAR(40),
    children_count INTEGER,
    business_status VARCHAR(40),
    business_age_months INTEGER,
    business_monthly_revenue NUMERIC(15, 2),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_profile_non_negative CHECK (
        (personal_monthly_income IS NULL OR personal_monthly_income >= 0)
        AND (household_monthly_income IS NULL OR household_monthly_income >= 0)
        AND (asset IS NULL OR asset >= 0)
        AND (children_count IS NULL OR children_count >= 0)
    )
);

CREATE TABLE simulation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE simulation_profile (
    simulation_id BIGINT PRIMARY KEY REFERENCES simulation(id) ON DELETE CASCADE,
    current_profile JSONB NOT NULL,
    changed_profile JSONB NOT NULL
);

CREATE TABLE simulation_result (
    id BIGSERIAL PRIMARY KEY,
    simulation_id BIGINT NOT NULL REFERENCES simulation(id) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    current_status VARCHAR(40) NOT NULL,
    simulated_status VARCHAR(40) NOT NULL,
    change_status VARCHAR(40) NOT NULL,
    result_payload JSONB NOT NULL,
    CONSTRAINT uq_simulation_policy UNIQUE (simulation_id, policy_id)
);

CREATE TABLE policy_bookmark (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, policy_id)
);

CREATE TABLE admin_review (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy(id) ON DELETE CASCADE,
    reason VARCHAR(80) NOT NULL,
    priority_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    assigned_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ
);

CREATE TABLE batch_execution_log (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(120) NOT NULL,
    execution_id BIGINT,
    status VARCHAR(40) NOT NULL,
    read_count INTEGER NOT NULL DEFAULT 0,
    write_count INTEGER NOT NULL DEFAULT 0,
    skip_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_policy_category_region ON policy(category, region_code);
CREATE INDEX idx_policy_application_period ON policy(application_start, application_end);
CREATE INDEX idx_policy_name_trgm ON policy USING GIN (name gin_trgm_ops);
CREATE INDEX idx_policy_search_document ON policy USING GIN (
    to_tsvector(
        'simple',
        coalesce(name, '') || ' ' || coalesce(description, '') || ' '
            || coalesce(target_description, '') || ' ' || coalesce(support_content, '') || ' '
            || coalesce(provider, '')
    )
);
CREATE INDEX idx_raw_policy_external_id ON raw_policy(source, external_id, collected_at DESC);
CREATE INDEX idx_admin_review_queue ON admin_review(status, priority_score DESC, created_at);

