package io.aidlink.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record AdminRuleUpdateRequest(@NotNull JsonNode rule) {
}
