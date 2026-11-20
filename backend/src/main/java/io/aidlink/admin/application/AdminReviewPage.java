package io.aidlink.admin.application;

import io.aidlink.admin.domain.AdminReview;

import java.util.List;

public record AdminReviewPage(
        List<AdminReview> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean first,
        boolean last
) {
    public AdminReviewPage {
        content = List.copyOf(content);
    }

    public static AdminReviewPage of(
            List<AdminReview> content,
            long totalElements,
            int page,
            int size
    ) {
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + size - 1) / size);
        return new AdminReviewPage(
                content,
                totalElements,
                totalPages,
                page,
                size,
                page == 0,
                totalPages == 0 || page >= totalPages - 1
        );
    }
}
