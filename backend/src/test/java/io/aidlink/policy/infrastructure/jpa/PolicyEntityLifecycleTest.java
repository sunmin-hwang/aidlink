package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEntityLifecycleTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Test
    void verifiedProviderPageReplacesOnlySourceFallbackNotDirectApplication() {
        PolicyEntity policy = policyEnding(null);
        ReflectionTestUtils.setField(policy, "verifiedSourceUrl", "https://provider.test/verified");
        assertThat(policy.getSourceUrl()).isEqualTo("https://provider.test/verified");
        assertThat(policy.getApplicationUrl()).isEqualTo("https://example.test/apply");

        ReflectionTestUtils.setField(policy, "applicationUrl", "https://example.test/source");
        assertThat(policy.getApplicationUrl()).isEqualTo("https://provider.test/verified");
        // A subsequent collection can update raw URLs without discarding verification.
        ReflectionTestUtils.setField(policy, "sourceUrl", "https://api.test/old");
        ReflectionTestUtils.setField(policy, "applicationUrl", "https://api.test/old");
        assertThat(policy.getSourceUrl()).isEqualTo("https://provider.test/verified");
        assertThat(policy.getApplicationUrl()).isEqualTo("https://provider.test/verified");
    }

    @Test
    void seenPolicyUsesApplicationEndAndResetsMissingCount() {
        PolicyEntity policy = policyEnding(LocalDate.now(SERVICE_ZONE).minusDays(1));

        policy.markMissed(3);
        policy.markSeen(Instant.parse("2026-11-13T00:00:00Z"));

        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.EXPIRED);
        assertThat(policy.getMissedCollectionCount()).isZero();
        assertThat(policy.getLastSeenAt()).isEqualTo(Instant.parse("2026-11-13T00:00:00Z"));
    }

    @Test
    void sourceRemovedRequiresThreeConsecutiveCompletedCollections() {
        PolicyEntity policy = policyEnding(null);
        policy.markSeen(Instant.parse("2026-11-13T00:00:00Z"));

        policy.markMissed(3);
        policy.markMissed(3);
        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.ACTIVE);

        policy.markMissed(3);
        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.SOURCE_REMOVED);
        assertThat(policy.getMissedCollectionCount()).isEqualTo(3);

        policy.markSeen(Instant.parse("2026-11-14T00:00:00Z"));
        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.ACTIVE);
        assertThat(policy.getMissedCollectionCount()).isZero();
    }

    @Test
    void archivedPolicyIsNotReactivatedByCollection() {
        PolicyEntity policy = policyEnding(null);
        policy.archive();
        Instant archivedAt = policy.getArchivedAt();

        policy.markSeen(Instant.parse("2026-11-13T00:00:00Z"));
        policy.markMissed(3);

        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.ARCHIVED);
        assertThat(policy.getMissedCollectionCount()).isZero();
        assertThat(policy.getArchivedAt()).isEqualTo(archivedAt).isNotNull();

        policy.restore();
        assertThat(policy.getLifecycleStatus()).isEqualTo(PolicyLifecycleStatus.ACTIVE);
        assertThat(policy.getArchivedAt()).isNull();
    }

    private PolicyEntity policyEnding(LocalDate applicationEnd) {
        return new PolicyEntity(
                "TEST-LIFECYCLE",
                "생명주기 테스트",
                "테스트 기관",
                "생활",
                "NATIONWIDE",
                "설명",
                "대상",
                "지원",
                null,
                applicationEnd,
                "온라인",
                "없음",
                "https://example.test/apply",
                "https://example.test/source",
                PolicyAnalysisStatus.RAW,
                null
        );
    }
}
