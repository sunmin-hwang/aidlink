package io.aidlink.policy.infrastructure.memory;

import io.aidlink.policy.application.PolicyBookmarkOperations;
import io.aidlink.policy.application.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!postgres")
public class InMemoryPolicyBookmarkService implements PolicyBookmarkOperations {

    private final Map<String, Set<Long>> bookmarks = new ConcurrentHashMap<>();

    @Override
    public PageResult<Long> findPolicyIds(String email, int page, int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<Long> ids = new ArrayList<>(bookmarks.getOrDefault(email, Set.of()));
        ids.sort(java.util.Comparator.reverseOrder());
        int total = ids.size();
        int totalPages = total == 0 ? 0 : (total + normalizedSize - 1) / normalizedSize;
        int normalizedPage = totalPages == 0 ? 0 : Math.min(Math.max(page, 0), totalPages - 1);
        int from = Math.min(normalizedPage * normalizedSize, total);
        int to = Math.min(from + normalizedSize, total);
        return new PageResult<>(ids.subList(from, to), total, normalizedPage, normalizedSize);
    }

    @Override
    public boolean contains(String email, long policyId) {
        return bookmarks.getOrDefault(email, Set.of()).contains(policyId);
    }

    @Override
    public void add(String email, long policyId) {
        bookmarks.computeIfAbsent(email, ignored -> ConcurrentHashMap.newKeySet()).add(policyId);
    }

    @Override
    public void remove(String email, long policyId) {
        bookmarks.computeIfPresent(email, (ignored, ids) -> {
            ids.remove(policyId);
            return ids.isEmpty() ? null : ids;
        });
    }
}
