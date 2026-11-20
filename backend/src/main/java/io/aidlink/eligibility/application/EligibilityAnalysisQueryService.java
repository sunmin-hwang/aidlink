package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.EligibilityAnalysis;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.domain.Policy;
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
public class EligibilityAnalysisQueryService {

    private static final List<EligibilityStatus> RECOMMENDED_ORDER = List.of(
            EligibilityStatus.ELIGIBLE,
            EligibilityStatus.NEAR_MISS,
            EligibilityStatus.UNKNOWN,
            EligibilityStatus.NOT_ELIGIBLE
    );

    private final EligibilityPolicyCatalog policyCatalog;
    private final EligibilityService eligibilityService;

    public EligibilityAnalysisQueryService(
            EligibilityPolicyCatalog policyCatalog,
            EligibilityService eligibilityService
    ) {
        this.policyCatalog = policyCatalog;
        this.eligibilityService = eligibilityService;
    }

    public EligibilityAnalysisPage analyze(
            UserProfile profile,
            LocalDate evaluationDate,
            EligibilityAnalysisCriteria criteria
    ) {
        List<AnalyzedPolicy> analyzedRules = policyCatalog
                .findWithRules(criteria.category(), criteria.regionCode())
                .stream()
                .map(policy -> new AnalyzedPolicy(
                        policy,
                        eligibilityService.analyze(policy, profile, evaluationDate)
                ))
                .toList();
        long noRuleCount = policyCatalog.countWithoutRules(
                criteria.category(),
                criteria.regionCode()
        );
        Map<EligibilityStatus, Long> summary = summarize(analyzedRules, noRuleCount);
        List<AnalyzedPolicy> matchingRules = analyzedRules.stream()
                .filter(analyzed -> criteria.status() == null
                        || analyzed.analysis().eligibilityStatus() == criteria.status())
                .toList();
        boolean includeNoRule = criteria.status() == null
                || criteria.status() == EligibilityStatus.UNKNOWN;
        long totalElements = matchingRules.size() + (includeNoRule ? noRuleCount : 0);
        int effectivePage = effectivePage(criteria.page(), totalElements, criteria.size());

        PageContent pageContent = switch (criteria.sort()) {
            case RECOMMENDED -> new PageContent(
                    recommendedContent(
                            analyzedRules,
                            noRuleCount,
                            includeNoRule,
                            profile,
                            evaluationDate,
                            criteria,
                            effectivePage
                    ),
                    totalElements,
                    effectivePage
            );
            case MISSING_COUNT -> new PageContent(
                    missingCountContent(
                            matchingRules,
                            noRuleCount,
                            includeNoRule,
                            profile,
                            evaluationDate,
                            criteria,
                            effectivePage
                    ),
                    totalElements,
                    effectivePage
            );
            case DEADLINE, NAME -> databaseOrderedContent(
                    analyzedRules,
                    profile,
                    evaluationDate,
                    criteria,
                    effectivePage
            );
        };

        int totalPages = totalPages(pageContent.totalElements(), criteria.size());
        return new EligibilityAnalysisPage(
                pageContent.content(),
                summary,
                policyCatalog.findCategories(),
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

    private List<EligibilityAnalysis> recommendedContent(
            List<AnalyzedPolicy> analyzedRules,
            long noRuleCount,
            boolean includeNoRule,
            UserProfile profile,
            LocalDate evaluationDate,
            EligibilityAnalysisCriteria criteria,
            int page
    ) {
        long start = (long) page * criteria.size();
        long end = start + criteria.size();
        long cursor = 0;
        List<EligibilityAnalysis> content = new ArrayList<>(criteria.size());
        List<EligibilityStatus> statuses = criteria.status() == null
                ? RECOMMENDED_ORDER
                : List.of(criteria.status());

        for (EligibilityStatus status : statuses) {
            List<EligibilityAnalysis> group = analyzedRules.stream()
                    .map(AnalyzedPolicy::analysis)
                    .filter(analysis -> analysis.eligibilityStatus() == status)
                    .sorted(Comparator.comparing(EligibilityAnalysis::policyName))
                    .toList();
            cursor = appendAnalysisSegment(group, cursor, start, end, content);
            if (status == EligibilityStatus.UNKNOWN && includeNoRule) {
                cursor = appendNoRuleSegment(
                        noRuleCount,
                        cursor,
                        start,
                        end,
                        content,
                        profile,
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

    private List<EligibilityAnalysis> missingCountContent(
            List<AnalyzedPolicy> matchingRules,
            long noRuleCount,
            boolean includeNoRule,
            UserProfile profile,
            LocalDate evaluationDate,
            EligibilityAnalysisCriteria criteria,
            int page
    ) {
        List<EligibilityAnalysis> orderedRules = matchingRules.stream()
                .map(AnalyzedPolicy::analysis)
                .sorted(Comparator
                        .comparingInt((EligibilityAnalysis analysis) ->
                                analysis.blockingConditions().size())
                        .thenComparingInt(analysis -> statusRank(
                                analysis.eligibilityStatus()))
                        .thenComparing(EligibilityAnalysis::policyName))
                .toList();
        long start = (long) page * criteria.size();
        long end = start + criteria.size();
        List<EligibilityAnalysis> content = new ArrayList<>(criteria.size());
        long cursor = appendAnalysisSegment(orderedRules, 0, start, end, content);
        if (includeNoRule && cursor < end) {
            appendNoRuleSegment(
                    noRuleCount,
                    cursor,
                    start,
                    end,
                    content,
                    profile,
                    evaluationDate,
                    criteria
            );
        }
        return content;
    }

    private PageContent databaseOrderedContent(
            List<AnalyzedPolicy> analyzedRules,
            UserProfile profile,
            LocalDate evaluationDate,
            EligibilityAnalysisCriteria criteria,
            int page
    ) {
        List<Long> excludedPolicyIds = criteria.status() == null
                ? List.of()
                : analyzedRules.stream()
                .filter(analyzed -> analyzed.analysis().eligibilityStatus() != criteria.status())
                .map(analyzed -> analyzed.policy().id())
                .toList();
        EligibilityRulePresence presence = criteria.status() != null
                && criteria.status() != EligibilityStatus.UNKNOWN
                ? EligibilityRulePresence.WITH
                : EligibilityRulePresence.ANY;
        EligibilityPolicySort policySort = criteria.sort() == EligibilityAnalysisSort.DEADLINE
                ? EligibilityPolicySort.DEADLINE
                : EligibilityPolicySort.NAME;
        PageResult<Policy> policies = policyCatalog.findPage(
                criteria.category(),
                criteria.regionCode(),
                excludedPolicyIds,
                presence,
                page,
                criteria.size(),
                policySort
        );
        Map<Long, EligibilityAnalysis> analysesById = new HashMap<>();
        analyzedRules.forEach(analyzed -> analysesById.put(
                analyzed.policy().id(),
                analyzed.analysis()
        ));
        List<EligibilityAnalysis> content = policies.content().stream()
                .map(policy -> analysisFor(
                        policy,
                        analysesById,
                        profile,
                        evaluationDate
                ))
                .toList();
        return new PageContent(
                content,
                policies.totalElements(),
                policies.page()
        );
    }

    private long appendAnalysisSegment(
            List<EligibilityAnalysis> segment,
            long cursor,
            long start,
            long end,
            List<EligibilityAnalysis> output
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
            List<EligibilityAnalysis> output,
            UserProfile profile,
            LocalDate evaluationDate,
            EligibilityAnalysisCriteria criteria
    ) {
        long segmentEnd = cursor + noRuleCount;
        if (start < segmentEnd && end > cursor) {
            long offset = Math.max(start, cursor) - cursor;
            int limit = Math.toIntExact(Math.min(end, segmentEnd) - Math.max(start, cursor));
            fetchNoRuleRange(criteria, offset, limit).stream()
                    .map(policy -> eligibilityService.analyze(
                            policy,
                            profile,
                            evaluationDate
                    ))
                    .forEach(output::add);
        }
        return segmentEnd;
    }

    private List<Policy> fetchNoRuleRange(
            EligibilityAnalysisCriteria criteria,
            long offset,
            int limit
    ) {
        if (limit == 0) {
            return List.of();
        }
        int fetchSize = criteria.size();
        int page = Math.toIntExact(offset / fetchSize);
        int skip = Math.toIntExact(offset % fetchSize);
        List<Policy> result = new ArrayList<>(limit);
        while (result.size() < limit) {
            PageResult<Policy> policies = policyCatalog.findPage(
                    criteria.category(),
                    criteria.regionCode(),
                    List.of(),
                    EligibilityRulePresence.WITHOUT,
                    page,
                    fetchSize,
                    EligibilityPolicySort.RECENT
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

    private Map<EligibilityStatus, Long> summarize(
            List<AnalyzedPolicy> analyzedRules,
            long noRuleCount
    ) {
        Map<EligibilityStatus, Long> summary = new EnumMap<>(EligibilityStatus.class);
        for (EligibilityStatus status : EligibilityStatus.values()) {
            summary.put(status, 0L);
        }
        analyzedRules.forEach(analyzed -> summary.merge(
                analyzed.analysis().eligibilityStatus(),
                1L,
                Long::sum
        ));
        summary.merge(EligibilityStatus.UNKNOWN, noRuleCount, Long::sum);
        return summary;
    }

    private EligibilityAnalysis analysisFor(
            Policy policy,
            Map<Long, EligibilityAnalysis> analysesById,
            UserProfile profile,
            LocalDate evaluationDate
    ) {
        EligibilityAnalysis existing = analysesById.get(policy.id());
        return existing != null
                ? existing
                : eligibilityService.analyze(policy, profile, evaluationDate);
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

    private int statusRank(EligibilityStatus status) {
        return RECOMMENDED_ORDER.indexOf(status);
    }

    private record AnalyzedPolicy(Policy policy, EligibilityAnalysis analysis) {
    }

    private record PageContent(
            List<EligibilityAnalysis> content,
            long totalElements,
            int page
    ) {
    }
}
