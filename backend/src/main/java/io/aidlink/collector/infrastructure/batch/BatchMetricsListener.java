package io.aidlink.collector.infrastructure.batch;

import io.aidlink.observability.AidLinkMetrics;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("postgres")
public class BatchMetricsListener implements JobExecutionListener {

    private final AidLinkMetrics metrics;

    public BatchMetricsListener(AidLinkMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long read = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getReadCount()).sum();
        long written = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getWriteCount()).sum();
        long skipped = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getSkipCount()).sum();
        Duration duration = jobExecution.getStartTime() == null
                || jobExecution.getEndTime() == null
                ? Duration.ZERO
                : Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
        metrics.recordBatch(
                jobExecution.getStatus() == BatchStatus.COMPLETED,
                duration,
                read,
                written,
                skipped
        );
    }
}
