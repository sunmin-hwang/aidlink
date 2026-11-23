package io.aidlink.collector.infrastructure.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Gov24DailySyncTest {
    private final JobLauncher launcher = mock(JobLauncher.class);
    private final Job job = mock(Job.class);
    private final JobRepository repository = mock(JobRepository.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Instant now = Instant.parse("2026-11-23T00:15:00Z"); // 09:15 KST

    @BeforeEach
    void setUp() throws Exception {
        when(job.getName()).thenReturn("gov24PolicySyncJob");
        when(explorer.findRunningJobExecutions(any())).thenReturn(Set.of());
        JobExecution completed = new JobExecution(1L);
        completed.setStatus(BatchStatus.COMPLETED);
        when(launcher.run(eq(job), any())).thenReturn(completed);
    }

    private Gov24DailySync sync(Instant instant, boolean enabled) {
        return new Gov24DailySync(launcher, job, repository, explorer, enabled,
                LocalTime.of(3, 0), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void delayedWakeUsesKoreanDateAndDoesNotRepeatAfterCompletion() throws Exception {
        Gov24DailySync sync = sync(now, true);
        sync.collectIfDue();
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getString("collectionDate")).isEqualTo("2026-11-23");
        assertThat(parameters.getValue().getLong("pageSize")).isEqualTo(500L);
        JobExecution completed = new JobExecution(1L, parameters.getValue());
        completed.setStatus(BatchStatus.COMPLETED);
        when(repository.getLastJobExecution(eq(job.getName()), any())).thenReturn(completed);
        // Simulate a new service instance after a restart: DB completion is authoritative.
        sync(now, true).collectIfDue();
        verify(launcher, times(1)).run(eq(job), any());
        assertThat(sync.status().status()).isEqualTo("COMPLETED");
    }

    @Test
    void koreanCalendarDateIsUsedEvenWhenUtcIsStillPreviousDay() throws Exception {
        sync(Instant.parse("2026-11-22T18:01:00Z"), true).collectIfDue();
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getString("collectionDate")).isEqualTo("2026-11-23");
    }

    @Test
    void disabledOrBeforeThreeDoesNotCollect() throws Exception {
        sync(now, false).collectIfDue();
        sync(Instant.parse("2026-11-22T17:59:00Z"), true).collectIfDue();
        verifyNoInteractions(launcher);
        assertThat(sync(now, false).status().status()).isEqualTo("DISABLED");
        assertThat(sync(Instant.parse("2026-11-22T17:59:00Z"), true).status().status()).isEqualTo("NOT_DUE");
    }

    @Test
    void runningManualCollectionPreventsOverlap() throws Exception {
        when(explorer.findRunningJobExecutions(job.getName())).thenReturn(Set.of(new JobExecution(2L)));
        sync(now, true).collectIfDue();
        verifyNoInteractions(launcher);
    }

    @Test
    void failedCollectionRetriesSameInstanceOnlyAfterBackoff() throws Exception {
        JobExecution failed = new JobExecution(1L);
        failed.setStatus(BatchStatus.FAILED);
        failed.setEndTime(LocalDateTime.ofInstant(now.minusSeconds(60), ZoneId.systemDefault()));
        when(repository.getLastJobExecution(eq(job.getName()), any())).thenReturn(failed);
        sync(now, true).collectIfDue();
        verifyNoInteractions(launcher);
        failed.setEndTime(LocalDateTime.ofInstant(now.minusSeconds(301), ZoneId.systemDefault()));
        sync(now, true).collectIfDue();
        verify(launcher).run(eq(job), any());
    }

    @Test
    void uncleanShutdownRequiresReviewRatherThanStartingConcurrentJob() throws Exception {
        for (BatchStatus status : new BatchStatus[]{BatchStatus.STARTED, BatchStatus.UNKNOWN, BatchStatus.ABANDONED}) {
            JobExecution execution = new JobExecution(1L);
            execution.setStatus(status);
            when(repository.getLastJobExecution(eq(job.getName()), any())).thenReturn(execution);
            sync(now, true).collectIfDue();
        }
        verifyNoInteractions(launcher);
    }
}
