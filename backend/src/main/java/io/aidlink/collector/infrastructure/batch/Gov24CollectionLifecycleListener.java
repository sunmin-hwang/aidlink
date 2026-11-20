package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.domain.PolicySource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("postgres")
public class Gov24CollectionLifecycleListener implements JobExecutionListener {

    private static final String STARTED_AT_KEY = "gov24.lifecycle.startedAt";

    private final NormalizedPolicyStore normalizedPolicyStore;
    private final int sourceRemovedThreshold;

    public Gov24CollectionLifecycleListener(
            NormalizedPolicyStore normalizedPolicyStore,
            @Value("${aidlink.collector.gov24.source-removed-threshold:3}")
            int sourceRemovedThreshold
    ) {
        if (sourceRemovedThreshold < 2) {
            throw new IllegalArgumentException(
                    "Gov24 source-removed-threshold must be at least 2"
            );
        }
        this.normalizedPolicyStore = normalizedPolicyStore;
        this.sourceRemovedThreshold = sourceRemovedThreshold;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        jobExecution.getExecutionContext().putLong(
                STARTED_AT_KEY,
                Instant.now().toEpochMilli()
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            return;
        }
        Instant startedAt = Instant.ofEpochMilli(
                jobExecution.getExecutionContext().getLong(STARTED_AT_KEY)
        );
        try {
            normalizedPolicyStore.completeSuccessfulCollection(
                    PolicySource.GOV24,
                    startedAt,
                    sourceRemovedThreshold
            );
        } catch (RuntimeException exception) {
            jobExecution.addFailureException(exception);
            jobExecution.setStatus(BatchStatus.FAILED);
            jobExecution.setExitStatus(
                    ExitStatus.FAILED.addExitDescription(exception)
            );
        }
    }
}
