package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.Gov24PolicyNormalizer;
import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.application.PolicySourceClient;
import io.aidlink.collector.application.RawPolicyChangeDetector;
import io.aidlink.collector.application.RawPolicyStore;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.collector.domain.RawPolicyVersionCandidate;
import io.aidlink.observability.AidLinkMetrics;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@Profile("postgres")
public class Gov24BatchConfiguration {

    @Bean
    @StepScope
    ItemReader<RawPolicyDocument> gov24PolicyItemReader(
            List<PolicySourceClient> clients,
            @Value("#{jobParameters['pageSize'] != null ? jobParameters['pageSize'] : 500}")
            Long pageSize
    ) {
        PolicySourceClient client = clients.stream()
                .filter(candidate -> candidate.source() == PolicySource.GOV24)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gov24 source client is not configured"));
        return new Gov24PolicyItemReader(client, Math.toIntExact(pageSize));
    }

    @Bean
    ItemProcessor<RawPolicyDocument, RawPolicyVersionCandidate> gov24PolicyItemProcessor(
            RawPolicyChangeDetector changeDetector,
            Gov24PolicyNormalizer normalizer
    ) {
        return new Gov24PolicyItemProcessor(changeDetector, normalizer);
    }

    @Bean
    ItemWriter<RawPolicyVersionCandidate> gov24PolicyItemWriter(
            RawPolicyStore rawPolicyStore,
            NormalizedPolicyStore normalizedPolicyStore,
            AidLinkMetrics metrics
    ) {
        return new Gov24PolicyItemWriter(rawPolicyStore, normalizedPolicyStore, metrics);
    }

    @Bean
    Step gov24PolicySyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("gov24PolicyItemReader") ItemReader<RawPolicyDocument> reader,
            @Qualifier("gov24PolicyItemProcessor")
            ItemProcessor<RawPolicyDocument, RawPolicyVersionCandidate> processor,
            @Qualifier("gov24PolicyItemWriter") ItemWriter<RawPolicyVersionCandidate> writer
    ) {
        return new StepBuilder("gov24PolicySyncStep", jobRepository)
                .<RawPolicyDocument, RawPolicyVersionCandidate>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    Job gov24PolicySyncJob(
            JobRepository jobRepository,
            @Qualifier("gov24PolicySyncStep") Step step,
            BatchMetricsListener metricsListener,
            Gov24CollectionLifecycleListener lifecycleListener
    ) {
        return new JobBuilder("gov24PolicySyncJob", jobRepository)
                .start(step)
                .listener(metricsListener)
                .listener(lifecycleListener)
                .build();
    }
}
