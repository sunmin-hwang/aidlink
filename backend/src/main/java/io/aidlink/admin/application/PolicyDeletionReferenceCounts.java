package io.aidlink.admin.application;

public record PolicyDeletionReferenceCounts(
        long policySources,
        long policyRules,
        long policyRuleVersions,
        long policyRuleCandidates,
        long policyRuleValidations,
        long simulationResults,
        long bookmarks,
        long adminReviews,
        long activeAdminReviews,
        long subscriptionMatches,
        long rawPolicySnapshots
) {
    public long cascadingRows() {
        return policySources
                + policyRules
                + policyRuleVersions
                + policyRuleCandidates
                + policyRuleValidations
                + simulationResults
                + bookmarks
                + adminReviews
                + subscriptionMatches;
    }
}
