package io.aidlink.collector.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonHasherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonHasher hasher = new CanonicalJsonHasher(objectMapper);

    @Test
    void objectFieldOrderDoesNotChangeTheHash() throws Exception {
        var first = objectMapper.readTree("""
                {"서비스ID":"A-1","nested":{"b":2,"a":1}}
                """);
        var second = objectMapper.readTree("""
                {"nested":{"a":1,"b":2},"서비스ID":"A-1"}
                """);

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second));
    }
}
