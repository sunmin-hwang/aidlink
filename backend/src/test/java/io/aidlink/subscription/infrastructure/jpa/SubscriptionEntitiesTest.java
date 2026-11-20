package io.aidlink.subscription.infrastructure.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.subscription.domain.BriefingDeliveryStatus;
import io.aidlink.subscription.domain.SubscriptionStatus;
import io.aidlink.user.infrastructure.jpa.AppUserEntity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionEntitiesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String IDEMPOTENCY_KEY = "b".repeat(64);

    @Test
    void subscriptionSupportsOneOrTwoWeeklySlotsAndLifecycleChanges() {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        var subscription = subscription(now);
        var first = new SubscriptionScheduleEntity(
                subscription,
                1,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 30, 45),
                now
        );

        assertThat(first.getDeliveryTime()).isEqualTo(LocalTime.of(9, 30));
        assertThatThrownBy(() -> new SubscriptionScheduleEntity(
                subscription,
                3,
                DayOfWeek.FRIDAY,
                LocalTime.NOON,
                now
        )).isInstanceOf(IllegalArgumentException.class);

        subscription.updateUrgentDeadlineAlerts(true, now.plusSeconds(10));
        subscription.scheduleNextBriefing(now.plusSeconds(3600), now.plusSeconds(20));
        subscription.pause(now.plusSeconds(30));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(subscription.getNextBriefingAt()).isNull();

        subscription.resume(now.plusSeconds(7200), now.plusSeconds(40));
        subscription.cancel(now.plusSeconds(50));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> subscription.updateTimezone("UTC", now.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void policyMatchTracksStatusAndSourceChangesSinceThePreviousBriefing() throws Exception {
        Instant firstEvaluation = Instant.parse("2026-11-11T00:00:00Z");
        var subscription = subscription(firstEvaluation);
        var policy = policy();
        var match = new SubscriptionPolicyMatchEntity(
                subscription,
                policy,
                EligibilityStatus.UNKNOWN,
                OBJECT_MAPPER.readTree("{\"missing\":[\"income\"]}"),
                Instant.parse("2026-11-10T00:00:00Z"),
                firstEvaluation
        );

        boolean unchanged = match.refresh(
                EligibilityStatus.UNKNOWN,
                OBJECT_MAPPER.readTree("{\"missing\":[\"income\"]}"),
                Instant.parse("2026-11-10T00:00:00Z"),
                firstEvaluation.plusSeconds(60)
        );
        boolean changed = match.refresh(
                EligibilityStatus.NEAR_MISS,
                OBJECT_MAPPER.readTree("{\"missing\":[]}"),
                Instant.parse("2026-11-11T00:00:00Z"),
                firstEvaluation.plusSeconds(120)
        );

        assertThat(unchanged).isFalse();
        assertThat(changed).isTrue();
        assertThat(match.getPreviousStatus()).isEqualTo(EligibilityStatus.UNKNOWN);
        assertThat(match.getCurrentStatus()).isEqualTo(EligibilityStatus.NEAR_MISS);
    }

    @Test
    void briefingDeliveryKeepsIdempotencyRecipientSnapshotAndAttemptHistory() throws Exception {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        var delivery = new PolicyBriefingDeliveryEntity(
                subscription(now),
                IDEMPOTENCY_KEY,
                "User@Example.com",
                now.plusSeconds(3600),
                now.minusSeconds(604800),
                now,
                now
        );

        delivery.startAttempt(OBJECT_MAPPER.readTree("{\"newPolicies\":2}"), now.plusSeconds(10));
        delivery.markFailed("temporary provider failure", now.plusSeconds(20));
        delivery.startAttempt(OBJECT_MAPPER.readTree("{\"newPolicies\":2}"), now.plusSeconds(30));
        delivery.markSent("provider-message-1", now.plusSeconds(40));

        assertThat(delivery.getRecipientEmail()).isEqualTo("user@example.com");
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        assertThat(delivery.getStatus()).isEqualTo(BriefingDeliveryStatus.SENT);
        assertThat(delivery.getProviderMessageId()).isEqualTo("provider-message-1");
    }

    private static PolicySubscriptionEntity subscription(Instant now) {
        return new PolicySubscriptionEntity(
                new AppUserEntity("user@example.com", "hash", "ROLE_USER", now),
                "Asia/Seoul",
                now
        );
    }

    private static PolicyEntity policy() {
        return new PolicyEntity(
                "GOV24:test",
                "테스트 정책",
                "테스트 기관",
                "주거",
                "SEOUL",
                "설명",
                "대상",
                "지원 내용",
                null,
                null,
                "온라인",
                "없음",
                "https://example.com/apply",
                "https://example.com/source",
                PolicyAnalysisStatus.HUMAN_VERIFIED,
                Instant.parse("2026-11-10T00:00:00Z")
        );
    }
}
