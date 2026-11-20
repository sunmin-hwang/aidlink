package io.aidlink.collector.application;

import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.PolicyCollectionReport;
import io.aidlink.collector.domain.PolicyCollectionResult;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.springframework.stereotype.Service;
import io.aidlink.observability.AidLinkMetrics;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PolicyCollector {

    private final List<PolicySourceClient> clients;
    private final RawPolicyStore rawPolicyStore;
    private final RawPolicyChangeDetector changeDetector;
    private final AidLinkMetrics metrics;

    public PolicyCollector(
            List<PolicySourceClient> clients,
            RawPolicyStore rawPolicyStore,
            RawPolicyChangeDetector changeDetector,
            AidLinkMetrics metrics
    ) {
        this.clients = List.copyOf(clients);
        this.rawPolicyStore = rawPolicyStore;
        this.changeDetector = changeDetector;
        this.metrics = metrics;
    }

    @Transactional
    public PolicyCollectionReport collectPage(PolicySource source, int page, int perPage) {
        PolicySourceClient client = clients.stream()
                .filter(candidate -> candidate.source() == source)
                .reduce((first, second) -> {
                    throw new IllegalStateException("Duplicate client for " + source);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "No policy source client registered for " + source));

        CollectedPolicyPage collectedPage = client.fetchPage(page, perPage);
        Instant collectedAt = Instant.now();
        List<PolicyCollectionResult> results = collectedPage.policies().stream()
                .map(document -> storeIfChanged(document, collectedAt))
                .toList();
        results.forEach(result -> metrics.recordPolicyChange(result.changeType()));

        int created = count(results, CollectionChangeType.CREATED);
        int updated = count(results, CollectionChangeType.UPDATED);
        int unchanged = count(results, CollectionChangeType.UNCHANGED);

        return new PolicyCollectionReport(
                source,
                collectedPage.page(),
                collectedPage.perPage(),
                collectedPage.totalCount(),
                results.size(),
                created + updated,
                created,
                updated,
                unchanged,
                results
        );
    }

    private PolicyCollectionResult storeIfChanged(RawPolicyDocument document, Instant collectedAt) {
        var detected = changeDetector.detect(document);
        if (detected.changeType() == CollectionChangeType.UNCHANGED) {
            return new PolicyCollectionResult(
                    document.externalId(), detected.contentHash(), CollectionChangeType.UNCHANGED);
        }

        rawPolicyStore.save(document, detected.contentHash(), collectedAt);
        return new PolicyCollectionResult(
                document.externalId(), detected.contentHash(), detected.changeType());
    }

    private int count(List<PolicyCollectionResult> results, CollectionChangeType type) {
        return (int) results.stream().filter(result -> result.changeType() == type).count();
    }
}
