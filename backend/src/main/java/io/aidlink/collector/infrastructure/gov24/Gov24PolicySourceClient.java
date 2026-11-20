package io.aidlink.collector.infrastructure.gov24;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aidlink.collector.application.PolicySourceClient;
import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.observability.AidLinkMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class Gov24PolicySourceClient implements PolicySourceClient {

    private static final List<String> ID_FIELDS = List.of("서비스ID", "SVC_ID", "svcId", "serviceId");
    private static final List<String> NAME_FIELDS = List.of("서비스명", "serviceName");

    private final RestClient restClient;
    private final Gov24ApiProperties properties;
    private final AidLinkMetrics metrics;

    public Gov24PolicySourceClient(
            @Qualifier("gov24RestClient") RestClient restClient,
            Gov24ApiProperties properties,
            AidLinkMetrics metrics
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public PolicySource source() {
        return PolicySource.GOV24;
    }

    @Override
    public CollectedPolicyPage fetchPage(int page, int perPage) {
        if (page < 1 || perPage < 1 || perPage > 1_000) {
            throw new IllegalArgumentException("page must be positive and perPage must be between 1 and 1000");
        }
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            throw new IllegalStateException(
                    "Gov24 API key is missing. Set AIDLINK_GOV24_SERVICE_KEY before collecting policies."
            );
        }

        try {
            CollectedPolicyPage listPage = parsePage(
                    fetchPage("/serviceList", page, perPage), page, perPage);
            CollectedPolicyPage detailPage = parsePage(
                    fetchPage("/serviceDetail", page, perPage), page, perPage);
            CollectedPolicyPage result = mergePages(listPage, detailPage);
            metrics.recordGov24Success();
            return result;
        } catch (RuntimeException exception) {
            metrics.recordGov24Failure();
            throw exception;
        }
    }

    private JsonNode fetchPage(String path, int page, int perPage) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("page", page)
                        .queryParam("perPage", perPage)
                        .queryParam("returnType", "JSON")
                        .queryParam("serviceKey", properties.getServiceKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    CollectedPolicyPage mergePages(
            CollectedPolicyPage listPage,
            CollectedPolicyPage detailPage
    ) {
        Map<String, RawPolicyDocument> detailsById = detailPage.policies().stream()
                .collect(Collectors.toMap(
                        RawPolicyDocument::externalId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<RawPolicyDocument> merged = listPage.policies().stream()
                .map(summary -> merge(summary, detailsById.get(summary.externalId())))
                .toList();
        return new CollectedPolicyPage(
                listPage.page(), listPage.perPage(), listPage.totalCount(), merged);
    }

    private RawPolicyDocument merge(
            RawPolicyDocument summary,
            RawPolicyDocument detail
    ) {
        if (detail == null || !detail.payload().isObject()) {
            return summary;
        }
        ObjectNode merged = ((ObjectNode) summary.payload()).deepCopy();
        detail.payload().properties().forEach(
                entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
        return new RawPolicyDocument(summary.source(), summary.externalId(), merged);
    }

    CollectedPolicyPage parsePage(JsonNode response, int requestedPage, int requestedPerPage) {
        if (response == null || !response.isObject()) {
            throw new IllegalStateException("Gov24 returned an empty or invalid response");
        }
        JsonNode data = response.get("data");
        JsonNode totalCountNode = response.get("totalCount");
        if (data == null || !data.isArray() || totalCountNode == null || !totalCountNode.canConvertToLong()) {
            throw new IllegalStateException(
                    "Gov24 response contract changed: data array or totalCount is missing"
            );
        }

        List<RawPolicyDocument> policies = new ArrayList<>();
        Set<String> seenExternalIds = new HashSet<>();
        for (JsonNode item : data) {
            if (!item.isObject()) {
                throw new IllegalStateException("Gov24 response contains a non-object policy item");
            }
            String externalId = firstText(item, ID_FIELDS);
            String name = firstText(item, NAME_FIELDS);
            if (externalId == null || name == null) {
                throw new IllegalStateException(
                        "Gov24 response contract changed: a policy is missing its ID or service name"
                );
            }
            if (!seenExternalIds.add(externalId)) {
                throw new IllegalStateException("Gov24 returned a duplicate service ID: " + externalId);
            }
            policies.add(new RawPolicyDocument(PolicySource.GOV24, externalId, item));
        }

        int actualPage = positiveIntOrDefault(response.get("page"), requestedPage);
        int actualPerPage = positiveIntOrDefault(response.get("perPage"), requestedPerPage);
        return new CollectedPolicyPage(actualPage, actualPerPage, totalCountNode.longValue(), policies);
    }

    private String firstText(JsonNode item, List<String> candidates) {
        for (String candidate : candidates) {
            JsonNode value = item.get(candidate);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private int positiveIntOrDefault(JsonNode value, int defaultValue) {
        return value != null && value.canConvertToInt() && value.intValue() > 0
                ? value.intValue()
                : defaultValue;
    }
}
