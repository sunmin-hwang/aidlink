package io.aidlink.ai.application;

import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
@ConditionalOnProperty(
        prefix = "aidlink.ai.pipeline",
        name = "scheduling-enabled",
        havingValue = "true"
)
public class PolicyAnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyAnalysisScheduler.class);

    private final PolicyCatalog policyCatalog;
    private final PolicyRuleParsingService parsingService;
    private final PolicyRuleValidationService validationService;
    private final int maxPerRun;

    public PolicyAnalysisScheduler(
            PolicyCatalog policyCatalog,
            PolicyRuleParsingService parsingService,
            PolicyRuleValidationService validationService,
            @Value("${aidlink.ai.pipeline.max-per-run:50}") int maxPerRun
    ) {
        this.policyCatalog = policyCatalog;
        this.parsingService = parsingService;
        this.validationService = validationService;
        this.maxPerRun = Math.clamp(maxPerRun, 1, 500);
    }

    @Scheduled(cron = "${aidlink.ai.pipeline.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void analyzePendingPolicies() {
        policyCatalog.findAll().stream()
                .filter(policy -> policy.analysisStatus() == PolicyAnalysisStatus.RAW
                        || policy.analysisStatus() == PolicyAnalysisStatus.AI_PARSED)
                .limit(maxPerRun)
                .forEach(policy -> analyzeOne(policy.id(), policy.analysisStatus()));
    }

    private void analyzeOne(long policyId, PolicyAnalysisStatus status) {
        try {
            if (status == PolicyAnalysisStatus.RAW) {
                parsingService.parsePolicy(policyId);
            }
            var validation = validationService.validateLatest(policyId);
            log.info(
                    "event=POLICY_ANALYSIS_COMPLETED policyId={} validationStatus={} score={}",
                    policyId,
                    validation.status(),
                    validation.score()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=POLICY_ANALYSIS_FAILED policyId={} errorType={}",
                    policyId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
