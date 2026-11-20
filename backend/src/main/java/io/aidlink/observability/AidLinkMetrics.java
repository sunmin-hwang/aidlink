package io.aidlink.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.aidlink.ai.domain.RuleValidationStatus;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.eligibility.domain.EligibilityStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AidLinkMetrics {

    private final MeterRegistry registry;
    private final Timer eligibilityDuration;
    private final Timer aiDuration;
    private final Timer batchDuration;
    private final AtomicInteger gov24ConsecutiveFailures = new AtomicInteger();
    private final double aiInputUsdPerMillion;
    private final double aiOutputUsdPerMillion;

    public AidLinkMetrics(MeterRegistry registry) {
        this(registry, 0, 0);
    }

    @Autowired
    public AidLinkMetrics(
            MeterRegistry registry,
            @Value("${aidlink.ai.cost.input-usd-per-million:0}") double aiInputUsdPerMillion,
            @Value("${aidlink.ai.cost.output-usd-per-million:0}") double aiOutputUsdPerMillion
    ) {
        this.registry = registry;
        this.aiInputUsdPerMillion = aiInputUsdPerMillion;
        this.aiOutputUsdPerMillion = aiOutputUsdPerMillion;
        this.eligibilityDuration = Timer.builder("eligibility.evaluation.duration")
                .description("Deterministic eligibility rule evaluation duration")
                .publishPercentileHistogram()
                .register(registry);
        this.aiDuration = Timer.builder("ai.request.duration")
                .description("Policy rule LLM request duration")
                .publishPercentileHistogram()
                .register(registry);
        this.batchDuration = Timer.builder("batch.duration")
                .description("Policy synchronization batch duration")
                .publishPercentileHistogram()
                .register(registry);
        registry.gauge("government.api.consecutive.failures",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("source", "GOV24")),
                gov24ConsecutiveFailures);
    }

    public void recordPolicyChange(CollectionChangeType changeType) {
        registry.counter("policy.collected").increment();
        switch (changeType) {
            case CREATED -> registry.counter("policy.created").increment();
            case UPDATED -> registry.counter("policy.updated").increment();
            case UNCHANGED -> { }
        }
    }

    public void recordEligibility(EligibilityStatus status, Duration duration) {
        registry.counter("eligibility.evaluation", "status", status.name()).increment();
        if (status == EligibilityStatus.UNKNOWN) {
            registry.counter("eligibility.unknown").increment();
        }
        eligibilityDuration.record(duration);
    }

    public void recordSimulation() {
        registry.counter("simulation").increment();
    }

    public void recordAiRequest(
            boolean success,
            Duration duration,
            long inputTokens,
            long outputTokens
    ) {
        registry.counter("ai.request", "outcome", success ? "success" : "failure").increment();
        if (!success) {
            registry.counter("ai.request.failure").increment();
        }
        registry.counter("ai.token.usage", "type", "input").increment(inputTokens);
        registry.counter("ai.token.usage", "type", "output").increment(outputTokens);
        double estimatedCost = (inputTokens * aiInputUsdPerMillion
                + outputTokens * aiOutputUsdPerMillion) / 1_000_000d;
        registry.counter("ai.estimated.cost", "currency", "USD").increment(estimatedCost);
        aiDuration.record(duration);
    }

    public void recordValidation(RuleValidationStatus status) {
        registry.counter("policy.validation", "status", status.name()).increment();
        if (status == RuleValidationStatus.REVIEW_REQUIRED) {
            registry.counter("policy.validation.failure").increment();
        }
    }

    public void recordBatch(
            boolean success,
            Duration duration,
            long readCount,
            long writeCount,
            long skipCount
    ) {
        registry.counter("batch.execution", "outcome", success ? "success" : "failure").increment();
        registry.counter("batch.items.read").increment(readCount);
        registry.counter("batch.items.written").increment(writeCount);
        registry.counter("batch.items.skipped").increment(skipCount);
        batchDuration.record(duration);
    }

    public void recordGov24Success() {
        gov24ConsecutiveFailures.set(0);
    }

    public void recordGov24Failure() {
        gov24ConsecutiveFailures.incrementAndGet();
        registry.counter("government.api.failure", "source", "GOV24").increment();
    }
}
