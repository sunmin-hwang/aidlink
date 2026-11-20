package io.aidlink.simulation.application;

import io.aidlink.eligibility.application.EligibilityPolicyCatalog;
import io.aidlink.eligibility.application.EligibilityPolicySort;
import io.aidlink.eligibility.application.EligibilityRulePresence;
import io.aidlink.eligibility.application.EligibilityService;
import io.aidlink.eligibility.domain.EligibilityAnalysis;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.observability.AidLinkMetrics;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.domain.Policy;
import io.aidlink.simulation.domain.SimulationChangeStatus;
import io.aidlink.simulation.domain.SimulationPolicyResult;
import io.aidlink.simulation.domain.SimulationResult;
import io.aidlink.user.domain.ProfileChanges;
import io.aidlink.user.domain.UserProfile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulationService {

    private static final List<SimulationChangeStatus> IMPORTANCE_ORDER = List.of(
            SimulationChangeStatus.NEWLY_ELIGIBLE,
            SimulationChangeStatus.LOST_ELIGIBILITY,
            SimulationChangeStatus.UNCHANGED_ELIGIBLE,
            SimulationChangeStatus.UNKNOWN_CHANGED,
            SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE
    );

    private final EligibilityPolicyCatalog policyCatalog;
    private final EligibilityService eligibilityService;
    private final AidLinkMetrics metrics;

    public SimulationService(
            EligibilityPolicyCatalog policyCatalog,
            EligibilityService eligibilityService,
            AidLinkMetrics metrics
    ) {
        this.policyCatalog = policyCatalog;
        this.eligibilityService = eligibilityService;
        this.metrics = metrics;
    }

    public SimulationResult simulate(
            UserProfile currentProfile,
            ProfileChanges changes,
            LocalDate evaluationDate,
            SimulationCriteria criteria
    ) {
        UserProfile simulatedProfile = changes.applyTo(currentProfile);
        List<ComparedPolicy> comparedRules = policyCatalog.findWithRules(null, null)
                .stream()
                .map(policy -> new ComparedPolicy(
                        policy,
                        compare(policy, currentProfile, simulatedProfile, evaluationDate)
                ))
                .toList();
        long noRuleCount = policyCatalog.countWithoutRules(null, null);
        Map<SimulationChangeStatus, Long> summary = summarize(comparedRules, noRuleCount);
        List<ComparedPolicy> matchingRules = comparedRules.stream()
                .filter(compared -> criteria.status() == null
                        || compared.result().changeStatus() == criteria.status())
                .toList();
        boolean includeNoRule = criteria.status() == null
                || criteria.status() == SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE;
        long totalElements = matchingRules.size() + (includeNoRule ? noRuleCount : 0);
        int effectivePage = effectivePage(criteria.page(), totalElements, criteria.size());

        PageContent pageContent = switch (criteria.sort()) {
            case IMPORTANCE -> new PageContent(
                    importanceContent(
                            comparedRules,
                            noRuleCount,
                            includeNoRule,
                            currentProfile,
                            simulatedProfile,
                            evaluationDate,
                            criteria,
                            effectivePage
                    ),
                    totalElements,
                    effectivePage
            );
            case NAME -> databaseOrderedContent(
                    comparedRules,
                    currentProfile,
                    simulatedProfile,
                    evaluationDate,
                    criteria,
                    effectivePage
            );
        };

        int totalPages = totalPages(pageContent.totalElements(), criteria.size());
        metrics.recordSimulation();
        return new SimulationResult(
                pageContent.content(),
                summary,
                pageContent.totalElements(),
                totalPages,
                pageContent.page(),
                criteria.size(),
                criteria.sort().queryValue(),
                criteria.status(),
                pageContent.page() == 0,
                totalPages == 0 || pageContent.page() >= totalPages - 1
        );
    }

    private List<SimulationPolicyResult> importanceContent(
            List<ComparedPolicy> comparedRules,
            long noRuleCount,
            boolean includeNoRule,
            UserProfile currentProfile,
            UserProfile simulatedProfile,
            LocalDate evaluationDate,
            SimulationCriteria criteria,
            int page
    ) {
        long start = (long) page * criteria.size();
        long end = start + criteria.size();
        long cursor = 0;
        List<SimulationPolicyResult> content = new ArrayList<>(criteria.size());
        List<SimulationChangeStatus> statuses = criteria.status() == null
                ? IMPORTANCE_ORDER
                : List.of(criteria.status());

        for (SimulationChangeStatus status : statuses) {
            List<SimulationPolicyResult> group = comparedRules.stream()
                    .map(ComparedPolicy::result)
                    .filter(result -> result.changeStatus() == status)
                    .sorted(Comparator.comparing(SimulationPolicyResult::policyName))
                    .toList();
            cursor = appendSegment(group, cursor, start, end, content);
            if (status == SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE && includeNoRule) {
                cursor = appendNoRuleSegment(
                        noRuleCount,
                        cursor,
                        start,
                        end,
                        content,
                        currentProfile,
                        simulatedProfile,
                        evaluationDate,
                        criteria
                );
            }
            if (cursor >= end) {
                break;
            }
        }
        return content;
    }

    private PageContent databaseOrderedContent(
            List<ComparedPolicy> comparedRules,
            UserProfile currentProfile,
            UserProfile simulatedProfile,
            LocalDate evaluationDate,
            SimulationCriteria criteria,
            int page
    ) {
        List<Long> excludedPolicyIds = criteria.status() == null
                ? List.of()
                : comparedRules.stream()
                .filter(compared -> compared.result().changeStatus() != criteria.status())
                .map(compared -> compared.policy().id())
                .toList();
        EligibilityRulePresence presence = criteria.status() != null
                && criteria.status() != SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE
                ? EligibilityRulePresence.WITH
                : EligibilityRulePresence.ANY;
        PageResult<Policy> policies = policyCatalog.findPage(
                null,
                null,
                excludedPolicyIds,
                presence,
                page,
                criteria.size(),
                EligibilityPolicySort.NAME
        );
        Map<Long, SimulationPolicyResult> resultsById = new HashMap<>();
        comparedRules.forEach(compared -> resultsById.put(
                compared.policy().id(),
                compared.result()
        ));
        List<SimulationPolicyResult> content = policies.content().stream()
                .map(policy -> resultFor(
                        policy,
                        resultsById,
                        currentProfile,
                        simulatedProfile,
                        evaluationDate
                ))
                .toList();
        return new PageContent(content, policies.totalElements(), policies.page());
    }

    private long appendSegment(
            List<SimulationPolicyResult> segment,
            long cursor,
            long start,
            long end,
            List<SimulationPolicyResult> output
    ) {
        long segmentEnd = cursor + segment.size();
        if (start < segmentEnd && end > cursor) {
            int fromIndex = Math.toIntExact(Math.max(start, cursor) - cursor);
            int toIndex = Math.toIntExact(Math.min(end, segmentEnd) - cursor);
            output.addAll(segment.subList(fromIndex, toIndex));
        }
        return segmentEnd;
    }

    private long appendNoRuleSegment(
            long noRuleCount,
            long cursor,
            long start,
            long end,
            List<SimulationPolicyResult> output,
            UserProfile currentProfile,
            UserProfile simulatedProfile,
            LocalDate evaluationDate,
            SimulationCriteria criteria
    ) {
        long segmentEnd = cursor + noRuleCount;
        if (start < segmentEnd && end > cursor) {
            long offset = Math.max(start, cursor) - cursor;
            int limit = Math.toIntExact(Math.min(end, segmentEnd) - Math.max(start, cursor));
            fetchNoRuleRange(offset, limit, criteria.size()).stream()
                    .map(policy -> compare(
                            policy,
                            currentProfile,
                            simulatedProfile,
                            evaluationDate
                    ))
                    .forEach(output::add);
        }
        return segmentEnd;
    }

    private List<Policy> fetchNoRuleRange(long offset, int limit, int fetchSize) {
        if (limit == 0) {
            return List.of();
        }
        int page = Math.toIntExact(offset / fetchSize);
        int skip = Math.toIntExact(offset % fetchSize);
        List<Policy> result = new ArrayList<>(limit);
        while (result.size() < limit) {
            PageResult<Policy> policies = policyCatalog.findPage(
                    null,
                    null,
                    List.of(),
                    EligibilityRulePresence.WITHOUT,
                    page,
                    fetchSize,
                    EligibilityPolicySort.NAME
            );
            int fromIndex = Math.min(skip, policies.content().size());
            int count = Math.min(limit - result.size(), policies.content().size() - fromIndex);
            if (count <= 0) {
                break;
            }
            result.addAll(policies.content().subList(fromIndex, fromIndex + count));
            page++;
            skip = 0;
        }
        return result;
    }

    private Map<SimulationChangeStatus, Long> summarize(
            List<ComparedPolicy> comparedRules,
            long noRuleCount
    ) {
        Map<SimulationChangeStatus, Long> summary = new EnumMap<>(SimulationChangeStatus.class);
        for (SimulationChangeStatus status : SimulationChangeStatus.values()) {
            summary.put(status, 0L);
        }
        comparedRules.forEach(compared -> summary.merge(
                compared.result().changeStatus(),
                1L,
                Long::sum
        ));
        summary.merge(
                SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE,
                noRuleCount,
                Long::sum
        );
        return summary;
    }

    private SimulationPolicyResult resultFor(
            Policy policy,
            Map<Long, SimulationPolicyResult> resultsById,
            UserProfile currentProfile,
            UserProfile simulatedProfile,
            LocalDate evaluationDate
    ) {
        SimulationPolicyResult existing = resultsById.get(policy.id());
        return existing != null
                ? existing
                : compare(policy, currentProfile, simulatedProfile, evaluationDate);
    }

    private SimulationPolicyResult compare(
            Policy policy,
            UserProfile currentProfile,
            UserProfile simulatedProfile,
            LocalDate evaluationDate
    ) {
        EligibilityAnalysis current = eligibilityService.analyze(
                policy,
                currentProfile,
                evaluationDate
        );
        EligibilityAnalysis simulated = eligibilityService.analyze(
                policy,
                simulatedProfile,
                evaluationDate
        );
        SimulationChangeStatus change = classify(
                current.eligibilityStatus(),
                simulated.eligibilityStatus()
        );
        return new SimulationPolicyResult(
                policy.id(),
                policy.name(),
                change,
                current,
                simulated
        );
    }

    SimulationChangeStatus classify(EligibilityStatus current, EligibilityStatus simulated) {
        boolean currentEligible = current == EligibilityStatus.ELIGIBLE;
        boolean simulatedEligible = simulated == EligibilityStatus.ELIGIBLE;

        if (!currentEligible && simulatedEligible) {
            return SimulationChangeStatus.NEWLY_ELIGIBLE;
        }
        if (currentEligible && !simulatedEligible) {
            return SimulationChangeStatus.LOST_ELIGIBILITY;
        }
        if (currentEligible) {
            return SimulationChangeStatus.UNCHANGED_ELIGIBLE;
        }
        if (current != simulated
                && (current == EligibilityStatus.UNKNOWN || simulated == EligibilityStatus.UNKNOWN)) {
            return SimulationChangeStatus.UNKNOWN_CHANGED;
        }
        return SimulationChangeStatus.UNCHANGED_NOT_ELIGIBLE;
    }

    private int effectivePage(int requestedPage, long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(Math.min(requestedPage, (totalElements - 1) / size));
    }

    private int totalPages(long totalElements, int size) {
        return totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + size - 1) / size);
    }

    private record ComparedPolicy(Policy policy, SimulationPolicyResult result) {
    }

    private record PageContent(
            List<SimulationPolicyResult> content,
            long totalElements,
            int page
    ) {
    }
}
