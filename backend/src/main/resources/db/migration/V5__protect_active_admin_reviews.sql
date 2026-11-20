CREATE UNIQUE INDEX uq_admin_review_active_reason
    ON admin_review(policy_id, reason)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
