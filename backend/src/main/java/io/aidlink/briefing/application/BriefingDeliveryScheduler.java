package io.aidlink.briefing.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
@ConditionalOnProperty(name="aidlink.briefing.mail.scheduling-enabled",havingValue="true")
public class BriefingDeliveryScheduler {
    private final BriefingDeliveryService deliveries;
    public BriefingDeliveryScheduler(BriefingDeliveryService deliveries) { this.deliveries=deliveries; }
    @Scheduled(fixedDelayString="${aidlink.briefing.mail.poll-ms:10000}")
    public void dispatch() { deliveries.dispatchDue(); }
}
