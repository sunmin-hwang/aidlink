package io.aidlink.collector.domain;

import java.util.List;

public record PolicyCollectionReport(
        PolicySource source,
        int page,
        int perPage,
        long totalCount,
        int readCount,
        int writeCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        List<PolicyCollectionResult> results
) {
    public PolicyCollectionReport {
        results = List.copyOf(results);
    }
}
