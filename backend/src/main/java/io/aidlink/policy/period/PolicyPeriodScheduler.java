package io.aidlink.policy.period;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
public class PolicyPeriodScheduler {
    private final PolicyPeriodService service;
    public PolicyPeriodScheduler(PolicyPeriodService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${aidlink.periods.lifecycle-refresh-ms:60000}", initialDelay = 60000)
    public void refresh() { service.refreshLifecycle(); }
}
