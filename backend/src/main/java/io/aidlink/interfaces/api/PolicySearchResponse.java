package io.aidlink.interfaces.api;

import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PolicySearchSort;
import io.aidlink.policy.application.PublicPolicyView;

import java.util.List;

public record PolicySearchResponse(
        List<PublicPolicyView> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        String sort,
        boolean first,
        boolean last
) {
    public static PolicySearchResponse from(
            PageResult<PublicPolicyView> result,
            PolicySearchSort sort
    ) {
        return new PolicySearchResponse(
                result.content(),
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size(),
                sort.queryValue(),
                result.first(),
                result.last()
        );
    }
}
