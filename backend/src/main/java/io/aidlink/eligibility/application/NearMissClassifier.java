package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.eligibility.domain.EvaluationStatus;
import io.aidlink.eligibility.domain.RuleEvaluation;
import org.springframework.stereotype.Component;

@Component
public class NearMissClassifier {

    public EligibilityStatus classify(RuleEvaluation evaluation) {
        if (evaluation.status() == EvaluationStatus.PASS) {
            return EligibilityStatus.ELIGIBLE;
        }
        if (evaluation.status() == EvaluationStatus.UNKNOWN) {
            return EligibilityStatus.UNKNOWN;
        }

        long unknownCount = evaluation.relevantConditions().stream()
                .filter(it -> it.status() == EvaluationStatus.UNKNOWN)
                .count();
        long passCount = evaluation.relevantConditions().stream()
                .filter(it -> it.status() == EvaluationStatus.PASS)
                .count();
        int total = evaluation.relevantConditions().size();

        boolean nearMiss = unknownCount == 0
                && evaluation.blockingConditions().size() == 1
                && total >= 2
                && (double) passCount / total >= 0.75;
        return nearMiss ? EligibilityStatus.NEAR_MISS : EligibilityStatus.NOT_ELIGIBLE;
    }
}

