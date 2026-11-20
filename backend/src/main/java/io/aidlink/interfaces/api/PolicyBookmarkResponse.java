package io.aidlink.interfaces.api;

import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PublicPolicyView;

import java.util.List;

public record PolicyBookmarkResponse(
        List<PublicPolicyView> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean first,
        boolean last
) {
    public static PolicyBookmarkResponse from(PageResult<PublicPolicyView> result) {
        return new PolicyBookmarkResponse(
                result.content(),
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size(),
                result.first(),
                result.last()
        );
    }
}
