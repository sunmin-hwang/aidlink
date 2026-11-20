package io.aidlink.collector.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record RawPolicyDocument(
        PolicySource source,
        String externalId,
        JsonNode payload
) {
    public RawPolicyDocument {
        Objects.requireNonNull(source, "source");
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        Objects.requireNonNull(payload, "payload");
        payload = payload.deepCopy();
    }
}
