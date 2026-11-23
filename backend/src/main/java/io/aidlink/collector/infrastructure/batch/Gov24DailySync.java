package io.aidlink.collector.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** One persisted job instance per Korean calendar day, including delayed wake-ups. */
@Service
@Profile("postgres")
public class Gov24DailySync {
    private static final Logger log = LoggerFactory.getLogger(Gov24DailySync.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JobLauncher launcher;
    private final Job job;
    private final JobRepository repository;
    private final JobExplorer explorer;
    private final boolean enabled;
    private final LocalTime dailyTime;
    private final Clock clock;

    @Autowired
    public Gov24DailySync(
            JobLauncher launcher,
            @Qualifier("gov24PolicySyncJob") Job job,
            JobRepository repository,
            JobExplorer explorer,
            @Value("${aidlink.collector.gov24.scheduling-enabled:false}") boolean enabled,
            @Value("${aidlink.collector.gov24.daily-time:03:00}") String dailyTime
    ) {
        this(launcher, job, repository, explorer, enabled, LocalTime.parse(dailyTime), Clock.systemUTC());
    }

    Gov24DailySync(JobLauncher launcher, Job job, JobRepository repository, JobExplorer explorer,
                   boolean enabled, LocalTime dailyTime, Clock clock) {
        this.launcher = launcher;
        this.job = job;
        this.repository = repository;
        this.explorer = explorer;
        this.enabled = enabled;
        this.dailyTime = dailyTime;
        this.clock = clock;
    }

    private JobParameters parameters(LocalDate date) {
        return new JobParametersBuilder()
                .addString("collectionDate", date.toString())
                .addLong("pageSize", 500L, false)
                .toJobParameters();
    }

    public synchronized void collectIfDue() throws Exception {
        var now = LocalDateTime.now(clock.withZone(SEOUL));
        if (!enabled || now.toLocalTime().isBefore(dailyTime)) return;
        JobParameters parameters = parameters(now.toLocalDate());
        JobExecution previous = repository.getLastJobExecution(job.getName(), parameters);
        if (previous != null) {
            BatchStatus status = previous.getStatus();
            if (status == BatchStatus.COMPLETED || status.isRunning()
                    || status == BatchStatus.UNKNOWN || status == BatchStatus.ABANDONED) return;
            // Avoid hammering the government API after a failure. End times use the JVM zone.
            if (previous.getEndTime() != null && previous.getEndTime()
                    .atZone(ZoneId.systemDefault()).toInstant()
                    .plus(Duration.ofMinutes(5)).isAfter(clock.instant())) return;
        }
        // Also avoid overlapping a manually started full import.
        if (!explorer.findRunningJobExecutions(job.getName()).isEmpty()) return;
        log.info("Gov24 daily sync starting for collectionDate={}", now.toLocalDate());
        JobExecution execution = launcher.run(job, parameters);
        log.info("Gov24 daily sync finished collectionDate={} executionId={} status={}",
                now.toLocalDate(), execution.getId(), execution.getStatus());
    }

    public SyncStatus status() {
        var now = LocalDateTime.now(clock.withZone(SEOUL));
        if (!enabled) return new SyncStatus(false, now.toLocalDate(), "DISABLED", null, null, 0, 0);
        JobExecution execution = repository.getLastJobExecution(job.getName(), parameters(now.toLocalDate()));
        if (execution == null) return new SyncStatus(true, now.toLocalDate(),
                now.toLocalTime().isBefore(dailyTime) ? "NOT_DUE" : "PENDING", null, null, 0, 0);
        return new SyncStatus(true, now.toLocalDate(), execution.getStatus().name(),
                execution.getId(), execution.getEndTime(),
                execution.getStepExecutions().stream().mapToLong(step -> step.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(step -> step.getWriteCount()).sum());
    }

    public record SyncStatus(boolean enabled, LocalDate collectionDate, String status,
                             Long executionId, LocalDateTime endedAt, long readCount, long writeCount) { }
}
