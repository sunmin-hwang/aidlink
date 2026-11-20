package io.aidlink.admin.application;

import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PublicPolicyCache;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminPolicyLifecycleService {

    static final int MINIMUM_RETENTION_DAYS = 30;

    private final AdminPolicyLifecycleStore store;
    private final PublicPolicyCache publicPolicyCache;

    public AdminPolicyLifecycleService(
            AdminPolicyLifecycleStore store,
            PublicPolicyCache publicPolicyCache
    ) {
        this.store = store;
        this.publicPolicyCache = publicPolicyCache;
    }

    public AdminPolicyLifecyclePage findInactive(AdminPolicyLifecycleCriteria criteria) {
        PageResult<AdminPolicyLifecycleView> result = store.findInactive(criteria);
        return new AdminPolicyLifecyclePage(
                result.content(),
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size(),
                criteria.status(),
                criteria.query(),
                result.first(),
                result.last()
        );
    }

    public AdminPolicyLifecycleView restore(long policyId) {
        AdminPolicyLifecycleView current = requiredPolicy(policyId);
        if (current.lifecycleStatus() == PolicyLifecycleStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 활성 상태인 정책입니다."
            );
        }
        if (current.lifecycleStatus() == PolicyLifecycleStatus.EXPIRED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 기간이 끝난 정책은 활성 상태로 복구할 수 없습니다."
            );
        }
        store.restore(policyId);
        publicPolicyCache.evictAll();
        return requiredPolicy(policyId);
    }

    public PolicyDeletionImpact previewDeletion(long policyId) {
        AdminPolicyLifecycleView policy = requiredPolicy(policyId);
        PolicyDeletionReferenceCounts counts = store.findReferenceCounts(policyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "정책을 찾을 수 없습니다."
                ));
        Instant now = Instant.now();
        Instant retentionUntil = policy.archivedAt() == null
                ? null
                : policy.archivedAt().plus(Duration.ofDays(MINIMUM_RETENTION_DAYS));
        List<String> blockers = blockers(policy, counts, retentionUntil, now);

        return new PolicyDeletionImpact(
                policy.policyId(),
                policy.policyName(),
                policy.lifecycleStatus(),
                true,
                MINIMUM_RETENTION_DAYS,
                policy.archivedAt(),
                retentionUntil,
                counts,
                counts.cascadingRows(),
                counts.rawPolicySnapshots(),
                blockers.isEmpty(),
                blockers,
                now
        );
    }

    private AdminPolicyLifecycleView requiredPolicy(long policyId) {
        return store.findById(policyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "정책을 찾을 수 없습니다."
                ));
    }

    private List<String> blockers(
            AdminPolicyLifecycleView policy,
            PolicyDeletionReferenceCounts counts,
            Instant retentionUntil,
            Instant now
    ) {
        List<String> blockers = new ArrayList<>();
        if (policy.lifecycleStatus() != PolicyLifecycleStatus.ARCHIVED) {
            blockers.add("ARCHIVED 상태의 정책만 영구 삭제 검토 대상입니다.");
        }
        if (retentionUntil == null) {
            blockers.add("보관 시각을 확인할 수 없습니다.");
        } else if (retentionUntil.isAfter(now)) {
            blockers.add("최소 보관 기간 30일이 지나지 않았습니다.");
        }
        addReferenceBlocker(blockers, counts.bookmarks(), "사용자 북마크");
        addReferenceBlocker(blockers, counts.simulationResults(), "과거 시뮬레이션 결과");
        addReferenceBlocker(blockers, counts.subscriptionMatches(), "구독 정책 매칭");
        addReferenceBlocker(blockers, counts.activeAdminReviews(), "진행 중 관리자 검수");
        addReferenceBlocker(blockers, counts.adminReviews(), "관리자 검수 이력");
        addReferenceBlocker(blockers, counts.policySources(), "정책 출처 연결");
        addReferenceBlocker(blockers, counts.policyRules(), "현재 구조화 Rule");
        addReferenceBlocker(blockers, counts.policyRuleVersions(), "Rule 버전 이력");
        addReferenceBlocker(blockers, counts.policyRuleCandidates(), "AI Rule 후보 이력");
        addReferenceBlocker(blockers, counts.policyRuleValidations(), "AI Rule 검증 이력");
        if (counts.rawPolicySnapshots() == 0) {
            blockers.add("보존할 원본 raw_policy 스냅샷이 없습니다.");
        }
        return blockers;
    }

    private void addReferenceBlocker(List<String> blockers, long count, String label) {
        if (count > 0) {
            blockers.add(label + " " + count + "건이 연결되어 있습니다.");
        }
    }
}
