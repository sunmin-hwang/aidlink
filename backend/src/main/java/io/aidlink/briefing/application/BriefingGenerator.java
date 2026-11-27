package io.aidlink.briefing.application;

import io.aidlink.eligibility.application.*;
import io.aidlink.eligibility.domain.*;
import io.aidlink.policy.application.PublicPolicyLink;
import io.aidlink.policy.domain.*;
import io.aidlink.policy.period.ApplicationWindow;
import io.aidlink.user.domain.UserProfile;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class BriefingGenerator {
    private final EligibilityPolicyCatalog policies;
    private final EligibilityService eligibility;

    public BriefingGenerator(EligibilityPolicyCatalog policies, EligibilityService eligibility) {
        this.policies = policies;
        this.eligibility = eligibility;
    }

    public BriefingContent generate(UserProfile profile, Instant now) {
        var localNow = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"));
        // Evaluate structured candidates, then a bounded sample of unstructured candidates.
        var candidates = new LinkedHashMap<Long, Policy>();
        policies.findWithRules(null, profile.regionCode()).forEach(p -> candidates.put(p.id(), p));
        var unstructured = policies.findPage(null, profile.regionCode(), List.of(),
                EligibilityRulePresence.WITHOUT, 0, 100, EligibilityPolicySort.DEADLINE);
        unstructured.content().forEach(p -> candidates.put(p.id(), p));
        var available = candidates.values().stream()
                .filter(p -> p.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE)
                .filter(p -> !window(p).expired(localNow))
                .toList();
        var items = available.stream().map(p -> item(p, profile, localNow.toLocalDate()))
                .filter(p -> p.status() != EligibilityStatus.NOT_ELIGIBLE)
                .sorted(Comparator.comparingInt((BriefingContent.Item p) -> rank(p.status()))
                        .thenComparingInt(p -> evidenceRank(candidates.get(p.policyId())))
                        .thenComparing(BriefingContent.Item::applicationEnd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BriefingContent.Item::name)
                        .thenComparingLong(BriefingContent.Item::policyId))
                .limit(20).toList();
        return new BriefingContent(localNow.toLocalDate(), profile.regionCode(), available.size(),
                unstructured.totalElements() > unstructured.content().size(),
                "저장한 프로필과 지역 기준으로 선별한 최대 20개 정책입니다. 판정 결과와 조건 정리 상태를 우선하고, 같은 그룹에서는 마감이 가까운 순서로 정리합니다. 구조화 조건이 없는 정책은 기한순 최대 100개를 확인합니다. 검토 중인 조건과 확인 필요는 신청 가능 판정이 아닙니다. 최종 자격과 접수 일정은 공식 기관에서 확인해 주세요.", items);
    }

    private BriefingContent.Item item(Policy p, UserProfile profile, LocalDate date) {
        var result = eligibility.analyze(p, profile, date);
        boolean trusted = p.rule() != null && (p.analysisStatus() == PolicyAnalysisStatus.HUMAN_VERIFIED
                || p.analysisStatus() == PolicyAnalysisStatus.AUTO_VALIDATED);
        var status = trusted ? result.eligibilityStatus() : EligibilityStatus.UNKNOWN;
        var checks = new ArrayList<String>();
        if (!trusted) checks.add("자격조건의 구조화·검증이 충분하지 않습니다. 공식 대상 조건을 직접 확인해 주세요.");
        eligibility.relevantConditions(p, profile, date).stream().filter(c -> result.ruleStatus() != EvaluationStatus.PASS)
                .filter(c -> c.status() != EvaluationStatus.PASS)
                .map(c -> (c.description() == null ? "확인할 조건" : c.description()) + ": " + c.message())
                .distinct().forEach(checks::add);
        if (p.applicationPeriod().reviewRequired()) checks.add("신청기간이 변경되어 재확인이 필요합니다.");
        if (p.applicationPeriod().kind() == ApplicationWindow.Kind.UNKNOWN) checks.add("확정된 신청기간이 없습니다. 공식 공고를 확인해 주세요.");
        String reason = switch (status) {
            case ELIGIBLE -> "저장한 프로필이 현재 구조화된 자격조건을 충족합니다. 최종 심사는 기관에서 진행합니다.";
            case NEAR_MISS -> "일부 조건을 충족하지 못했습니다. 부족 조건과 공식 예외를 확인해 주세요.";
            case UNKNOWN -> "추가 확인이 필요한 정책입니다. 현재 정보만으로 신청 가능 여부를 확정할 수 없습니다.";
            case NOT_ELIGIBLE -> "현재 입력 조건으로는 대상이 아닙니다.";
        };
        return new BriefingContent.Item(p.id(), p.name(), p.provider(), p.category(), status,
                p.analysisStatus(), reason, List.copyOf(checks), p.applicationStart(), p.applicationEnd(),
                p.applicationPeriod(), PublicPolicyLink.usableUrl(p.sourceUrl()), PublicPolicyLink.usableUrl(p.applicationUrl()));
    }

    private ApplicationWindow window(Policy p) {
        var period = p.applicationPeriod();
        // A stale reviewed deadline must not silently discard a policy awaiting review.
        if (period.reviewRequired()) return ApplicationWindow.unknown();
        return new ApplicationWindow(period.kind(), p.applicationStart(), p.applicationEnd(), period.startTime(), period.endTime());
    }

    private int rank(EligibilityStatus status) {
        return switch (status) { case ELIGIBLE -> 0; case NEAR_MISS -> 1; case UNKNOWN -> 2; case NOT_ELIGIBLE -> 3; };
    }

    private int evidenceRank(Policy policy) {
        if (policy.rule() == null) return 3;
        return switch (policy.analysisStatus()) {
            case HUMAN_VERIFIED -> 0;
            case AUTO_VALIDATED -> 1;
            case REVIEW_REQUIRED, AI_PARSED -> 2;
            case RAW -> 3;
        };
    }
}
