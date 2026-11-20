package io.aidlink.collector.infrastructure.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.application.PolicySourceClient;
import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "AIDLINK_RUN_POSTGRES_TEST", matches = "true")
class Gov24BatchIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("gov24PolicySyncJob")
    private Job job;

    @Autowired
    private PolicyJpaRepository policyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicySourceClient sourceClient;

    @BeforeEach
    void setUpSource() throws Exception {
        when(sourceClient.source()).thenReturn(PolicySource.GOV24);
        var payload = objectMapper.readTree("""
                {
                  "서비스ID":"BATCH-TEST-1",
                  "서비스명":"배치 통합 테스트 정책",
                  "서비스목적요약":"배치 저장 검증",
                  "지원내용":"테스트 지원",
                  "소관기관명":"행정안전부",
                  "수정일시":"2026-10-08 10:00:00"
                }
                """);
        when(sourceClient.fetchPage(anyInt(), anyInt())).thenAnswer(invocation -> {
            int page = invocation.getArgument(0);
            int perPage = invocation.getArgument(1);
            return new CollectedPolicyPage(
                    page,
                    perPage,
                    1,
                    page == 1
                            ? List.of(new RawPolicyDocument(PolicySource.GOV24, "BATCH-TEST-1", payload))
                            : List.of()
            );
        });
    }

    @Test
    void batchJobPersistsRawAndNormalizedPolicy() throws Exception {
        var execution = jobLauncher.run(job, new JobParametersBuilder()
                .addLong("testRun", System.nanoTime())
                .addLong("pageSize", 100L)
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(policyRepository.findByExternalId("GOV24:BATCH-TEST-1"))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.getName()).isEqualTo("배치 통합 테스트 정책");
                    assertThat(policy.getAnalysisStatus().name()).isEqualTo("RAW");
                    assertThat(policy.getLifecycleStatus())
                            .isEqualTo(PolicyLifecycleStatus.ACTIVE);
                    assertThat(policy.getLastSeenAt()).isNotNull();
                    assertThat(policy.getMissedCollectionCount()).isZero();
                });
    }
}
