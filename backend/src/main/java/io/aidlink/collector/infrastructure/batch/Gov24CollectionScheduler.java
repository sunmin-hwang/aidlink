package io.aidlink.collector.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(Gov24CollectionScheduler.class);
    private final Gov24DailySync dailySync;

    public Gov24CollectionScheduler(Gov24DailySync dailySync) {
        this.dailySync = dailySync;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void collect() {
        try {
            dailySync.collectIfDue();
        } catch (Exception exception) {
            // HTTP client exception messages can contain the service key; don't log them.
            log.error("Gov24 daily sync could not run; retrying on next check. failureType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
