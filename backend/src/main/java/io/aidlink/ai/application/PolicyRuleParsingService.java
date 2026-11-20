package io.aidlink.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.collector.application.CanonicalJsonHasher;
import io.aidlink.control.application.OperationLock;
import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.observability.AidLinkMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class PolicyRuleParsingService {

    private final PolicyCatalog policyCatalog;
    private final PolicyRuleParser parser;
    private final PolicyRuleCandidateStore candidateStore;
    private final ObjectMapper objectMapper;
    private final CanonicalJsonHasher hasher;
    private final AdminReviewQueue reviewQueue;
    private final OperationLock operationLock;
    private final AidLinkMetrics metrics;

    public PolicyRuleParsingService(
            PolicyCatalog policyCatalog,
            PolicyRuleParser parser,
            PolicyRuleCandidateStore candidateStore,
            ObjectMapper objectMapper,
            CanonicalJsonHasher hasher,
            AdminReviewQueue reviewQueue,
            OperationLock operationLock,
            AidLinkMetrics metrics
    ) {
        this.policyCatalog = policyCatalog;
        this.parser = parser;
        this.candidateStore = candidateStore;
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.reviewQueue = reviewQueue;
        this.operationLock = operationLock;
        this.metrics = metrics;
    }

    public PolicyRuleCandidate parsePolicy(long policyId) {
        var policy = policyCatalog.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        PolicyQualificationText sourceText = new PolicyQualificationText(
                policy.id(),
                policy.name(),
                policy.targetDescription(),
                policy.description(),
                ""
        );
        String textHash = sourceTextHash(sourceText);
        var existing = candidateStore.findLatest(policyId);
        if (existing.isPresent() && existing.get().sourceTextHash().equals(textHash)) {
            return existing.get().candidate();
        }

        String lockKey = "ai-policy-parse:" + textHash;
        String lockToken = operationLock.tryAcquire(lockKey, Duration.ofMinutes(5))
                .orElseThrow(() -> new IllegalStateException(
                        "The same policy content is already being parsed"));
        try {
            long startedAt = System.nanoTime();
            PolicyRuleCandidate candidate;
            try {
                candidate = parser.parse(sourceText);
            } catch (RuntimeException exception) {
                metrics.recordAiRequest(
                        false,
                        Duration.ofNanos(System.nanoTime() - startedAt),
                        0,
                        0
                );
                reviewQueue.enqueue(
                        policyId,
                        AdminReviewReason.PARSING_FAILED,
                        BigDecimal.valueOf(100)
                );
                throw exception;
            }
            metrics.recordAiRequest(
                    true,
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    candidate.inputTokens(),
                    candidate.outputTokens()
            );
            candidateStore.save(policyId, textHash, candidate);
            return candidate;
        } finally {
            operationLock.release(lockKey, lockToken);
        }
    }

    private String sourceTextHash(PolicyQualificationText sourceText) {
        try {
            return hasher.hash(objectMapper.valueToTree(sourceText));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Could not serialize policy qualification text", exception);
        }
    }
}
