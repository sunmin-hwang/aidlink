package io.aidlink.policy.infrastructure;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.application.EligibilityPolicyCatalog;
import io.aidlink.eligibility.application.EligibilityPolicySort;
import io.aidlink.eligibility.application.EligibilityRulePresence;
import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PolicySearchCriteria;
import io.aidlink.policy.application.RuleConstraintMatcher;
import io.aidlink.policy.domain.Policy;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;

@Repository
@Profile("!postgres")
public class InMemoryPolicyCatalog implements PolicyCatalog, EligibilityPolicyCatalog {

    private final List<Policy> policies = List.of(
            new Policy(
                    1L,
                    "SAMPLE-SEOUL-HOUSING",
                    "서울 청년 월세 지원",
                    "서울특별시",
                    "주거",
                    "SEOUL",
                    "서울 거주 무주택 청년의 월세 부담을 지원합니다.",
                    "만 19~34세 서울 거주 무주택 청년",
                    "월 최대 20만원을 지원합니다.",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    "온라인 신청",
                    "신분증, 주민등록등본, 임대차계약서",
                    "https://example.go.kr/apply/seoul-housing",
                    "https://example.go.kr/policies/seoul-housing",
                    null,
                    PolicyAnalysisStatus.HUMAN_VERIFIED,
                    new AndRule(List.of(
                            ConditionRule.of(ConditionField.AGE, Operator.BETWEEN, "만 19~34세", "19", "34"),
                            ConditionRule.of(ConditionField.REGION, Operator.EQ, "서울특별시 거주", "SEOUL"),
                            ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "월 개인소득 250만원 이하", "2500000"),
                            ConditionRule.of(ConditionField.HOME_OWNERSHIP, Operator.EQ, "무주택", "NONE")
                    ))
            ),
            new Policy(
                    2L,
                    "SAMPLE-JOB-START",
                    "청년 구직활동 지원",
                    "고용지원공단",
                    "취업",
                    "NATIONWIDE",
                    "구직 중이거나 단시간 근로 중인 청년의 취업 준비를 지원합니다.",
                    "만 18~34세 구직자 또는 주 30시간 이하 근로자",
                    "취업 프로그램과 월별 활동비를 지원합니다.",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    "온라인 신청",
                    "구직활동계획서",
                    "https://example.go.kr/apply/job-start",
                    "https://example.go.kr/policies/job-start",
                    null,
                    PolicyAnalysisStatus.AUTO_VALIDATED,
                    new AndRule(List.of(
                            ConditionRule.of(ConditionField.AGE, Operator.BETWEEN, "만 18~34세", "18", "34"),
                            new OrRule(List.of(
                                    ConditionRule.of(ConditionField.EMPLOYMENT, Operator.IN, "구직 또는 실업 상태", "JOB_SEEKER", "UNEMPLOYED"),
                                    ConditionRule.of(ConditionField.WEEKLY_WORK_HOURS, Operator.LTE, "주 30시간 이하 근로", "30")
                            )),
                            ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "월 개인소득 300만원 이하", "3000000")
                    ))
            ),
            new Policy(
                    3L,
                    "SAMPLE-WORK-SETTLEMENT",
                    "청년 취업정착 지원",
                    "지역일자리진흥원",
                    "취업",
                    "NATIONWIDE",
                    "취업 초기 청년의 안정적인 근속을 지원합니다.",
                    "만 18~39세 신규 취업자",
                    "6개월 근속 시 정착지원금을 지급합니다.",
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 11, 30),
                    "고용주 확인 후 온라인 신청",
                    "재직증명서, 근로계약서",
                    "https://example.go.kr/apply/work-settlement",
                    "https://example.go.kr/policies/work-settlement",
                    null,
                    PolicyAnalysisStatus.AI_PARSED,
                    new AndRule(List.of(
                            ConditionRule.of(ConditionField.AGE, Operator.BETWEEN, "만 18~39세", "18", "39"),
                            ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "현재 취업 상태", "EMPLOYED"),
                            ConditionRule.of(ConditionField.WEEKLY_WORK_HOURS, Operator.GTE, "주 20시간 이상 근로", "20"),
                            ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.BETWEEN, "월 개인소득 200~400만원", "2000000", "4000000")
                    ))
            ),
            new Policy(
                    4L,
                    "SAMPLE-NEWLYWED-HOUSING",
                    "신혼부부 주거안정 지원",
                    "주거복지공단",
                    "주거",
                    "NATIONWIDE",
                    "무주택 신혼부부의 주거비를 지원합니다.",
                    "혼인 중인 무주택 가구 중 소득기준 충족 가구",
                    "보증금 대출이자 일부를 지원합니다.",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    "관할 기관 방문 신청",
                    "혼인관계증명서, 주민등록등본, 소득증빙서류",
                    "https://example.go.kr/apply/newlywed-housing",
                    "https://example.go.kr/policies/newlywed-housing",
                    null,
                    PolicyAnalysisStatus.AUTO_VALIDATED,
                    new AndRule(List.of(
                            ConditionRule.of(ConditionField.MARITAL_STATUS, Operator.EQ, "혼인 상태", "MARRIED"),
                            ConditionRule.of(ConditionField.HOME_OWNERSHIP, Operator.EQ, "무주택 가구", "NONE"),
                            ConditionRule.of(ConditionField.HOUSEHOLD_INCOME, Operator.LTE, "가구 월소득 600만원 이하", "6000000"),
                            new OrRule(List.of(
                                    ConditionRule.of(ConditionField.REGION, Operator.EQ, "수도권 거주", "SEOUL"),
                                    ConditionRule.of(ConditionField.REGION, Operator.EQ, "수도권 거주", "GYEONGGI"),
                                    ConditionRule.of(ConditionField.REGION, Operator.EQ, "수도권 거주", "INCHEON")
                            ))
                    ))
            ),
            new Policy(
                    5L,
                    "SAMPLE-MANUAL-CARE",
                    "긴급 생활안정 지원",
                    "복지지원센터",
                    "생활",
                    "NATIONWIDE",
                    "위기 상황 가구를 위한 긴급 지원입니다.",
                    "기관이 지원 필요성을 인정한 가구",
                    "상황에 따라 생계비를 지원합니다.",
                    null,
                    null,
                    "복지지원센터 상담",
                    "상담 후 안내",
                    "https://example.go.kr/apply/emergency-care",
                    "https://example.go.kr/policies/emergency-care",
                    null,
                    PolicyAnalysisStatus.RAW,
                    null
            )
    );

    @Override
    public List<Policy> findAll() {
        return policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .toList();
    }

    @Override
    public long count() {
        return policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .count();
    }

    @Override
    public PageResult<Policy> search(PolicySearchCriteria criteria) {
        LocalDate today = LocalDate.now();
        List<Policy> matches = policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .filter(policy -> textMatches(policy, criteria.query()))
                .filter(policy -> criteria.category() == null || policy.category().equals(criteria.category()))
                .filter(policy -> criteria.regionCode() == null
                        || policy.regionCode().equals(criteria.regionCode())
                        || policy.regionCode().equals("NATIONWIDE"))
                .filter(policy -> criteria.provider() == null
                        || policy.provider().toLowerCase(Locale.ROOT)
                        .contains(criteria.provider().toLowerCase(Locale.ROOT)))
                .filter(policy -> !criteria.openOnly()
                        || (policy.applicationStart() == null || !policy.applicationStart().isAfter(today))
                        && (policy.applicationEnd() == null || !policy.applicationEnd().isBefore(today)))
                .filter(policy -> RuleConstraintMatcher.matches(
                        policy.rule(), ConditionField.AGE, criteria.age()))
                .filter(policy -> RuleConstraintMatcher.matches(
                        policy.rule(), ConditionField.EMPLOYMENT, criteria.employmentStatus()))
                .filter(policy -> RuleConstraintMatcher.matches(
                        policy.rule(), ConditionField.HOUSEHOLD_TYPE, criteria.householdType()))
                .sorted(comparator(criteria))
                .toList();
        int effectivePage = effectivePage(criteria.page(), matches.size(), criteria.size());
        int fromIndex = Math.min(effectivePage * criteria.size(), matches.size());
        int toIndex = Math.min(fromIndex + criteria.size(), matches.size());
        return new PageResult<>(
                matches.subList(fromIndex, toIndex),
                matches.size(),
                effectivePage,
                criteria.size()
        );
    }

    private Comparator<Policy> comparator(PolicySearchCriteria criteria) {
        return switch (criteria.sort()) {
            case RELEVANCE -> Comparator
                    .comparingInt((Policy policy) -> relevanceScore(policy, criteria.query()))
                    .reversed()
                    .thenComparingLong(Policy::id);
            case DEADLINE -> Comparator
                    .comparing(Policy::applicationEnd,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(Policy::id);
            case RECENT -> Comparator
                    .comparing(Policy::sourceUpdatedAt,
                            Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                    .thenComparing(Comparator.comparingLong(Policy::id).reversed());
            case NAME -> Comparator.comparing(Policy::name).thenComparingLong(Policy::id);
        };
    }

    private int relevanceScore(Policy policy, String query) {
        if (query == null) {
            return 0;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        int score = policy.name().toLowerCase(Locale.ROOT).contains(needle) ? 4 : 0;
        score += policy.provider().toLowerCase(Locale.ROOT).contains(needle) ? 2 : 0;
        score += String.join(" ", policy.description(), policy.targetDescription(), policy.supportContent())
                .toLowerCase(Locale.ROOT)
                .contains(needle) ? 1 : 0;
        return score;
    }

    private int effectivePage(int requestedPage, int totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.min(requestedPage, (totalElements - 1) / size);
    }

    @Override
    public List<Policy> findWithRules(String category, String regionCode) {
        return eligibilityPolicies(category, regionCode)
                .filter(policy -> policy.rule() != null)
                .toList();
    }

    @Override
    public long countWithoutRules(String category, String regionCode) {
        return eligibilityPolicies(category, regionCode)
                .filter(policy -> policy.rule() == null)
                .count();
    }

    @Override
    public PageResult<Policy> findPage(
            String category,
            String regionCode,
            List<Long> excludedPolicyIds,
            EligibilityRulePresence rulePresence,
            int page,
            int size,
            EligibilityPolicySort sort
    ) {
        Set<Long> excluded = Set.copyOf(excludedPolicyIds);
        List<Policy> matches = eligibilityPolicies(category, regionCode)
                .filter(policy -> !excluded.contains(policy.id()))
                .filter(policy -> switch (rulePresence) {
                    case ANY -> true;
                    case WITH -> policy.rule() != null;
                    case WITHOUT -> policy.rule() == null;
                })
                .sorted(eligibilityComparator(sort))
                .toList();
        int effectivePage = effectivePage(page, matches.size(), size);
        int fromIndex = Math.min(effectivePage * size, matches.size());
        int toIndex = Math.min(fromIndex + size, matches.size());
        return new PageResult<>(
                matches.subList(fromIndex, toIndex),
                matches.size(),
                effectivePage,
                size
        );
    }

    @Override
    public List<String> findCategories() {
        return policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .map(Policy::category)
                .distinct()
                .sorted()
                .toList();
    }

    private java.util.stream.Stream<Policy> eligibilityPolicies(
            String category,
            String regionCode
    ) {
        return policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .filter(policy -> category == null || policy.category().equals(category))
                .filter(policy -> regionCode == null
                        || policy.regionCode().equals(regionCode)
                        || policy.regionCode().equals("NATIONWIDE"));
    }

    private Comparator<Policy> eligibilityComparator(EligibilityPolicySort sort) {
        return switch (sort) {
            case RECENT -> Comparator
                    .comparing(Policy::sourceUpdatedAt,
                            Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                    .thenComparing(Comparator.comparingLong(Policy::id).reversed());
            case DEADLINE -> Comparator
                    .comparing(Policy::applicationEnd,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(Policy::id);
            case NAME -> Comparator.comparing(Policy::name).thenComparingLong(Policy::id);
        };
    }

    private boolean textMatches(Policy policy, String query) {
        if (query == null) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return String.join(" ",
                        policy.name(), policy.description(), policy.targetDescription(),
                        policy.supportContent(), policy.provider())
                .toLowerCase(Locale.ROOT)
                .contains(needle);
    }

    @Override
    public Optional<Policy> findById(long policyId) {
        return policies.stream()
                .filter(policy -> policy.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .filter(policy -> policy.id() == policyId)
                .findFirst();
    }
}
