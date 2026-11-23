package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.application.PolicyBookmarkOperations;
import io.aidlink.policy.application.PageResult;
import io.aidlink.user.infrastructure.jpa.AppUserJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("postgres")
public class JpaPolicyBookmarkService implements PolicyBookmarkOperations {

    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserJpaRepository users;
    private final JdbcTemplate jdbcTemplate;

    public JpaPolicyBookmarkService(AppUserJpaRepository users, JdbcTemplate jdbcTemplate) {
        this.users = users;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Long> findPolicyIds(String email, int page, int size) {
        long userId = requiredUserId(email);
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int requestedPage = Math.max(page, 0);
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM policy_bookmark bookmark
                JOIN policy ON policy.id = bookmark.policy_id
                WHERE bookmark.user_id = ?
                  AND policy.lifecycle_status IN ('ACTIVE', 'EXPIRED')
                """,
                Long.class,
                userId
        );
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + normalizedSize - 1) / normalizedSize);
        int normalizedPage = totalPages == 0
                ? 0
                : Math.min(requestedPage, totalPages - 1);
        List<Long> policyIds = jdbcTemplate.queryForList(
                """
                SELECT bookmark.policy_id
                FROM policy_bookmark bookmark
                JOIN policy ON policy.id = bookmark.policy_id
                WHERE bookmark.user_id = ?
                  AND policy.lifecycle_status IN ('ACTIVE', 'EXPIRED')
                ORDER BY bookmark.created_at DESC, bookmark.policy_id DESC
                LIMIT ? OFFSET ?
                """,
                Long.class,
                userId,
                normalizedSize,
                normalizedPage * normalizedSize
        );
        return new PageResult<>(policyIds, totalElements, normalizedPage, normalizedSize);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean contains(String email, long policyId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy_bookmark WHERE user_id = ? AND policy_id = ?",
                Long.class,
                requiredUserId(email),
                policyId
        );
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void add(String email, long policyId) {
        jdbcTemplate.update(
                """
                INSERT INTO policy_bookmark (user_id, policy_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, policy_id) DO NOTHING
                """,
                requiredUserId(email), policyId
        );
    }

    @Override
    @Transactional
    public void remove(String email, long policyId) {
        jdbcTemplate.update(
                "DELETE FROM policy_bookmark WHERE user_id = ? AND policy_id = ?",
                requiredUserId(email), policyId
        );
    }

    private long requiredUserId(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
