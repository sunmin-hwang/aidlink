package io.aidlink.admin.infrastructure.memory;

import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.admin.domain.AdminReviewStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAdminReviewQueueTest {

    @Test
    void deduplicatesActiveReviewsAndKeepsTheHighestPriority() {
        var queue = new InMemoryAdminReviewQueue();

        var first = queue.enqueue(1L, AdminReviewReason.VALIDATION_FAILED,
                BigDecimal.valueOf(30));
        var duplicate = queue.enqueue(1L, AdminReviewReason.VALIDATION_FAILED,
                BigDecimal.valueOf(70));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(queue.findOpenReviews(0, 20).content()).singleElement()
                .satisfies(review -> assertThat(review.priorityScore())
                        .isEqualByComparingTo("70"));

        queue.resolveForPolicy(1L);

        assertThat(queue.findOpenReviews(0, 20).content()).isEmpty();
        assertThat(duplicate.status()).isEqualTo(AdminReviewStatus.OPEN);
    }
}
