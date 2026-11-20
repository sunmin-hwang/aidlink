package io.aidlink.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.policy.application.PolicyCatalog;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessGaugeConfiguration {

    public BusinessGaugeConfiguration(
            MeterRegistry registry,
            PolicyCatalog policyCatalog,
            AdminReviewQueue reviewQueue
    ) {
        Gauge.builder("policy.total", policyCatalog, PolicyCatalog::count)
                .description("Current policies in the catalog")
                .register(registry);
        Gauge.builder("admin.review.queue.size", reviewQueue, AdminReviewQueue::countOpenReviews)
                .description("Open or in-progress policy reviews")
                .register(registry);
    }
}
