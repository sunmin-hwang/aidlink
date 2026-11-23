package io.aidlink.policy.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
@ConditionalOnProperty(prefix = "aidlink.links", name = "scheduling-enabled", havingValue = "true")
public class PolicyLinkScheduler {
    private static final Logger log = LoggerFactory.getLogger(PolicyLinkScheduler.class);
    private final PolicyLinkService service;
    private final int batchSize;

    public PolicyLinkScheduler(PolicyLinkService service, @Value("${aidlink.links.batch-size:20}") int batchSize) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${aidlink.links.cron:0 */30 * * * *}", zone = "Asia/Seoul")
    public void check() {
        log.info("Policy source link checks: {}", service.checkDue(batchSize));
    }
}
