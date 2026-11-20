package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.PolicySource;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.item.ExecutionContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gov24CollectionLifecycleListenerTest {

    @Test
    void onlyCompletedFullCollectionReconcilesMissingPolicies() {
        FakeNormalizedPolicyStore store = new FakeNormalizedPolicyStore();
        Gov24CollectionLifecycleListener listener =
                new Gov24CollectionLifecycleListener(store, 3);

        JobExecution failed = executionWithStatus(BatchStatus.FAILED);
        listener.beforeJob(failed);
        listener.afterJob(failed);
        assertThat(store.completedCollectionCount).isZero();

        JobExecution completed = executionWithStatus(BatchStatus.COMPLETED);
        listener.beforeJob(completed);
        listener.afterJob(completed);

        assertThat(store.completedCollectionCount).isEqualTo(1);
        assertThat(store.source).isEqualTo(PolicySource.GOV24);
        assertThat(store.threshold).isEqualTo(3);
        assertThat(store.collectionStartedAt).isNotNull();
    }

    private JobExecution executionWithStatus(BatchStatus status) {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getExecutionContext()).thenReturn(new ExecutionContext());
        when(execution.getStatus()).thenReturn(status);
        return execution;
    }

    private static class FakeNormalizedPolicyStore implements NormalizedPolicyStore {
        private int completedCollectionCount;
        private PolicySource source;
        private Instant collectionStartedAt;
        private int threshold;

        @Override
        public void upsert(
                NormalizedPolicy policy,
                CollectionChangeType changeType,
                Instant collectedAt
        ) {
        }

        @Override
        public void markSeen(
                PolicySource source,
                String sourceExternalId,
                Instant collectedAt
        ) {
        }

        @Override
        public void completeSuccessfulCollection(
                PolicySource source,
                Instant collectionStartedAt,
                int sourceRemovedThreshold
        ) {
            completedCollectionCount++;
            this.source = source;
            this.collectionStartedAt = collectionStartedAt;
            threshold = sourceRemovedThreshold;
        }
    }
}
