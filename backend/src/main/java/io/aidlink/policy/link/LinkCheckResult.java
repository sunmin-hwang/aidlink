package io.aidlink.policy.link;

public record LinkCheckResult(Status status, String finalUrl, Integer httpStatus, String reason) {
    public enum Status {
        UNCHECKED, CONTENT_MATCH, OTHER_PAGE, ERROR_PAGE, MAINTENANCE, CONNECTION_FAILED, BLOCKED_URL
    }
}
