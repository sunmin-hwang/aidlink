package io.aidlink.policy.application;

public record PolicySearchCriteria(
        String query,
        String category,
        String regionCode,
        String provider,
        boolean openOnly,
        Integer age,
        String employmentStatus,
        String householdType,
        int page,
        int size,
        PolicySearchSort sort
) {
    public PolicySearchCriteria {
        query = normalize(query);
        category = normalize(category);
        regionCode = normalize(regionCode);
        provider = normalize(provider);
        employmentStatus = normalize(employmentStatus);
        householdType = normalize(householdType);
        page = Math.max(page, 0);
        size = Math.clamp(size, 1, 100);
        sort = sort == null
                ? PolicySearchSort.resolve(null, query)
                : sort;
    }

    public boolean hasRuleConstraints() {
        return age != null || employmentStatus != null || householdType != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
