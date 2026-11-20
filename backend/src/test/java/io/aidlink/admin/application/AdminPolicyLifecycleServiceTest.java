package io.aidlink.admin.application;

import io.aidlink.policy.application.PublicPolicyCache;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPolicyLifecycleServiceTest {

    @Test
    void restoreEvictsPublicPolicyCacheAndReturnsNewStatus() {
        AdminPolicyLifecycleStore store = mock(AdminPolicyLifecycleStore.class);
        PublicPolicyCache cache = mock(PublicPolicyCache.class);
        AdminPolicyLifecycleView archived = policy(
                PolicyLifecycleStatus.ARCHIVED,
                Instant.now().minusSeconds(60L * 60 * 24 * 45)
        );
        AdminPolicyLifecycleView active = policy(PolicyLifecycleStatus.ACTIVE, null);
        when(store.findById(7L))
                .thenReturn(Optional.of(archived), Optional.of(active));
        AdminPolicyLifecycleService service = new AdminPolicyLifecycleService(store, cache);

        AdminPolicyLifecycleView restored = service.restore(7L);

        assertThat(restored.lifecycleStatus()).isEqualTo(PolicyLifecycleStatus.ACTIVE);
        verify(store).restore(7L);
        verify(cache).evictAll();
    }

    @Test
    void dryRunIsSafeOnlyAfterRetentionWithNoCascadingReferencesAndRawSnapshot() {
        AdminPolicyLifecycleStore store = mock(AdminPolicyLifecycleStore.class);
        PublicPolicyCache cache = mock(PublicPolicyCache.class);
        AdminPolicyLifecycleView archived = policy(
                PolicyLifecycleStatus.ARCHIVED,
                Instant.now().minusSeconds(60L * 60 * 24 * 45)
        );
        PolicyDeletionReferenceCounts counts = counts(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2
        );
        when(store.findById(7L)).thenReturn(Optional.of(archived));
        when(store.findReferenceCounts(7L)).thenReturn(Optional.of(counts));
        AdminPolicyLifecycleService service = new AdminPolicyLifecycleService(store, cache);

        PolicyDeletionImpact impact = service.previewDeletion(7L);

        assertThat(impact.dryRun()).isTrue();
        assertThat(impact.safeToDelete()).isTrue();
        assertThat(impact.blockers()).isEmpty();
        assertThat(impact.cascadingRows()).isZero();
        assertThat(impact.preservedRawPolicyRows()).isEqualTo(2);
    }

    @Test
    void dryRunListsRetentionAndReferenceBlockersWithoutDeletingAnything() {
        AdminPolicyLifecycleStore store = mock(AdminPolicyLifecycleStore.class);
        PublicPolicyCache cache = mock(PublicPolicyCache.class);
        AdminPolicyLifecycleView archived = policy(
                PolicyLifecycleStatus.ARCHIVED,
                Instant.now().minusSeconds(60L * 60 * 24 * 3)
        );
        PolicyDeletionReferenceCounts counts = counts(
                1, 1, 2, 3, 3, 4, 5, 2, 1, 6, 0
        );
        when(store.findById(7L)).thenReturn(Optional.of(archived));
        when(store.findReferenceCounts(7L)).thenReturn(Optional.of(counts));
        AdminPolicyLifecycleService service = new AdminPolicyLifecycleService(store, cache);

        PolicyDeletionImpact impact = service.previewDeletion(7L);

        assertThat(impact.safeToDelete()).isFalse();
        assertThat(impact.cascadingRows()).isEqualTo(27);
        assertThat(impact.blockers())
                .anyMatch(message -> message.contains("최소 보관 기간"))
                .anyMatch(message -> message.contains("사용자 북마크 5건"))
                .anyMatch(message -> message.contains("raw_policy"));
    }

    private AdminPolicyLifecycleView policy(
            PolicyLifecycleStatus status,
            Instant archivedAt
    ) {
        return new AdminPolicyLifecycleView(
                7L,
                "TEST-7",
                "삭제 영향 테스트 정책",
                "테스트 기관",
                "생활안정",
                status,
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                0,
                archivedAt
        );
    }

    private PolicyDeletionReferenceCounts counts(long... values) {
        return new PolicyDeletionReferenceCounts(
                values[0], values[1], values[2], values[3], values[4],
                values[5], values[6], values[7], values[8], values[9], values[10]
        );
    }
}
