package io.aidlink.policy.application;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> content,
        long totalElements,
        int page,
        int size
) {
    public PageResult {
        content = List.copyOf(content);
        totalElements = Math.max(totalElements, 0);
        page = Math.max(page, 0);
        size = Math.max(size, 1);
    }

    public int totalPages() {
        return totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + size - 1) / size);
    }

    public boolean first() {
        return page == 0;
    }

    public boolean last() {
        return totalPages() == 0 || page >= totalPages() - 1;
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                content.stream().map(mapper).toList(),
                totalElements,
                page,
                size
        );
    }
}
