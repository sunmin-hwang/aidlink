package io.aidlink.collector.infrastructure.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
@ConditionalOnProperty(
        prefix = "aidlink.collector.gov24",
        name = "scheduling-enabled",
        havingValue = "true"
)
public class Gov24CollectionScheduler {

    private final JobLauncher jobLauncher;
    private final Job job;

    public Gov24CollectionScheduler(
            JobLauncher jobLauncher,
            @Qualifier("gov24PolicySyncJob") Job job
    ) {
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    @Scheduled(cron = "${aidlink.collector.gov24.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void collect() throws Exception {
        jobLauncher.run(job, new JobParametersBuilder()
                .addLong("scheduledAt", System.currentTimeMillis())
                .addLong("pageSize", 500L)
                .toJobParameters());
    }
}
