package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.ConditionEvaluation;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.EligibilityAnalysis;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.eligibility.domain.EvaluationStatus;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.RuleEvaluation;
import io.aidlink.policy.domain.Policy;
import io.aidlink.user.domain.UserProfile;
import org.springframework.stereotype.Service;
import io.aidlink.observability.AidLinkMetrics;
import java.time.Duration;

import java.time.LocalDate;
import java.util.List;

@Service
public class EligibilityService {

    private final RuleEngine ruleEngine;
    private final NearMissClassifier nearMissClassifier;
    private final AidLinkMetrics metrics;

    public EligibilityService(
            RuleEngine ruleEngine,
            NearMissClassifier nearMissClassifier,
            AidLinkMetrics metrics
    ) {
        this.ruleEngine = ruleEngine;
        this.nearMissClassifier = nearMissClassifier;
        this.metrics = metrics;
    }

    public EligibilityAnalysis analyze(Policy policy, UserProfile profile, LocalDate evaluationDate) {
        long startedAt = System.nanoTime();
        EligibilityAnalysis analysis;
        if (policy.rule() == null) {
            ConditionEvaluation unknown = new ConditionEvaluation(
                    ConditionField.MANUAL_REVIEW,
                    Operator.REQUIRED,
                    List.of(),
                    null,
                    EvaluationStatus.UNKNOWN,
                    "구조화된 자격조건이 아직 없습니다.",
                    "관련 가능성이 있는 정책입니다. 현재 자동 자격 분석은 제공되지 않습니다."
            );
            analysis = createAnalysis(policy, EligibilityStatus.UNKNOWN,
                    new RuleEvaluation(EvaluationStatus.UNKNOWN, List.of(unknown), List.of()));
        } else {
            RuleEvaluation evaluation = ruleEngine.evaluate(policy.rule(), profile, evaluationDate);
            analysis = createAnalysis(policy, nearMissClassifier.classify(evaluation), evaluation);
        }
        metrics.recordEligibility(
                analysis.eligibilityStatus(),
                Duration.ofNanos(System.nanoTime() - startedAt)
        );
        return analysis;
    }

    /** Conditions on the selected AND/OR evaluation path, for explanations without unused branches. */
    public List<ConditionEvaluation> relevantConditions(Policy policy, UserProfile profile, LocalDate evaluationDate) {
        return policy.rule() == null ? List.of()
                : ruleEngine.evaluate(policy.rule(), profile, evaluationDate).relevantConditions();
    }

    private EligibilityAnalysis createAnalysis(
            Policy policy,
            EligibilityStatus eligibilityStatus,
            RuleEvaluation evaluation
    ) {
        return new EligibilityAnalysis(
                policy.id(),
                policy.name(),
                policy.provider(),
                policy.category(),
                policy.analysisStatus(),
                eligibilityStatus,
                evaluation.status(),
                evaluation.conditions(),
                evaluation.blockingConditions(),
                guidance(policy, eligibilityStatus)
        );
    }

    private String guidance(Policy policy, EligibilityStatus status) {
        return switch (policy.analysisStatus()) {
            case RAW -> "관련 가능성이 있는 정책입니다. 현재 자동 자격 분석은 제공되지 않습니다.";
            case AI_PARSED -> "자동 분석 결과입니다. 최종 신청 전 공식 조건을 확인해 주세요.";
            case AUTO_VALIDATED -> status == EligibilityStatus.ELIGIBLE
                    ? "현재 입력 조건 기준 지원 가능성이 높습니다."
                    : "자동 검증된 조건 기준 결과입니다. 최종 조건은 공식 페이지에서 확인해 주세요.";
            case HUMAN_VERIFIED -> "검증된 자격조건 기준 분석 결과입니다.";
            case REVIEW_REQUIRED -> "자동 분석 중 확인이 필요한 조건이 발견되었습니다. 공식 조건을 확인해 주세요.";
        };
    }
}
