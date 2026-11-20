package io.aidlink.admin.domain;

public enum AdminReviewReason {
    PARSING_FAILED,
    UNSUPPORTED_EXPRESSION,
    VALIDATION_FAILED,
    POLICY_CHANGED,
    USER_REPORT,
    HIGH_TRAFFIC_LOW_CONFIDENCE
}
