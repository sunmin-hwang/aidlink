package io.aidlink.collector.infrastructure.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.application.PolicySourceClient;
import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24PolicyItemReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resumesFromTheLastCommittedItem() throws Exception {
        var client = new FakeClient(5);
        var firstReader = new Gov24PolicyItemReader(client, 2);
        var context = new ExecutionContext();
        firstReader.open(context);

        assertThat(firstReader.read().externalId()).isEqualTo("SVC-1");
        assertThat(firstReader.read().externalId()).isEqualTo("SVC-2");
        assertThat(firstReader.read().externalId()).isEqualTo("SVC-3");
        firstReader.update(context);

        var restartedReader = new Gov24PolicyItemReader(client, 2);
        restartedReader.open(context);

        List<String> remaining = new ArrayList<>();
        RawPolicyDocument item;
        while ((item = restartedReader.read()) != null) {
            remaining.add(item.externalId());
        }

        assertThat(remaining).containsExactly("SVC-4", "SVC-5");
    }

    private class FakeClient implements PolicySourceClient {
        private final int total;

        private FakeClient(int total) {
            this.total = total;
        }

        @Override
        public PolicySource source() {
            return PolicySource.GOV24;
        }

        @Override
        public CollectedPolicyPage fetchPage(int page, int perPage) {
            int from = (page - 1) * perPage + 1;
            int to = Math.min(from + perPage - 1, total);
            List<RawPolicyDocument> policies = new ArrayList<>();
            for (int index = from; index <= to; index++) {
                policies.add(new RawPolicyDocument(
                        PolicySource.GOV24,
                        "SVC-" + index,
                        objectMapper.createObjectNode()
                                .put("서비스ID", "SVC-" + index)
                                .put("서비스명", "정책 " + index)
                ));
            }
            return new CollectedPolicyPage(page, perPage, total, policies);
        }
    }
}
