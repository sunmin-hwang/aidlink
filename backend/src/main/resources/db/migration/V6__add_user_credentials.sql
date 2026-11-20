ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100);

UPDATE app_user
SET password_hash = '{noop}disabled'
WHERE password_hash IS NULL;

ALTER TABLE app_user
    ALTER COLUMN password_hash SET NOT NULL;
