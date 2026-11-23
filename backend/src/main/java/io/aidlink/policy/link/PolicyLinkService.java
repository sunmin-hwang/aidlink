package io.aidlink.policy.link;

import io.aidlink.policy.application.PublicPolicyCache;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Profile("postgres")
public class PolicyLinkService {
    private static final long LOCK_KEY = 418632017;
    private final JdbcTemplate jdbc;
    private final PolicyLinkInspector inspector;
    private final PublicPolicyCache cache;

    public PolicyLinkService(JdbcTemplate jdbc, PolicyLinkInspector inspector, PublicPolicyCache cache) {
        this.jdbc = jdbc;
        this.inspector = inspector;
        this.cache = cache;
    }

    public record LinkView(long policyId, String policyName, String provider, String sourceUrl,
                           String verifiedSourceUrl, Instant verifiedAt, String checkedUrl, String status,
                           Instant checkedAt, String finalUrl, Integer httpStatus, String reason,
                           int failures, Instant nextCheckAt, String reviewedBy, String reviewEvidence) { }
    public record LinkPage(List<LinkView> content, long totalElements, int totalPages, int page, int size) { }

    private static final String PROJECTION = """
            SELECT id, name, provider, source_url, verified_source_url, source_link_verified_at,
                source_link_checked_url, source_link_status, source_link_checked_at,
                source_link_final_url, source_link_http_status, source_link_reason,
                source_link_failures, source_link_next_check_at, source_link_reviewed_by,
                source_link_review_evidence FROM policy
            """;

    public LinkPage list(int page, String status, String query) {
        if (page < 0) throw new IllegalArgumentException("페이지 번호를 확인해 주세요.");
        if (status != null && !status.isBlank()) LinkCheckResult.Status.valueOf(status);
        String filter = " WHERE lifecycle_status = 'ACTIVE' AND (? = '' OR source_link_status = ?)"
                + " AND (? = '' OR name ILIKE ? OR external_id ILIKE ?)";
        String selected = status == null ? "" : status;
        String q = query == null ? "" : query.trim();
        Object[] args = {selected, selected, q, "%" + q + "%", "%" + q + "%"};
        long total = jdbc.queryForObject("SELECT count(*) FROM policy" + filter, Long.class, args);
        int pages = (int) ((total + 19) / 20);
        int effective = Math.min(page, Math.max(0, pages - 1));
        List<LinkView> content = jdbc.query(PROJECTION + filter + " ORDER BY id LIMIT 20 OFFSET " + (effective * 20L),
                (rs, row) -> view(rs), args);
        return new LinkPage(content, total, pages, effective, 20);
    }

    public LinkView get(long id) {
        return jdbc.query(PROJECTION + " WHERE id = ?", (rs, row) -> view(rs), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "정책을 찾을 수 없습니다."));
    }

    public LinkView check(long id, String candidateUrl) {
        return locked(() -> inspectAndSave(id, candidateUrl));
    }

    private LinkView inspectAndSave(long id, String candidateUrl) {
        LinkView policy = get(id);
        String url = candidateUrl == null || candidateUrl.isBlank()
                ? (policy.verifiedSourceUrl() == null ? policy.sourceUrl() : policy.verifiedSourceUrl())
                : candidateUrl.trim();
        if (url.length() > 4000) throw new IllegalArgumentException("주소가 너무 깁니다.");
        LinkCheckResult result = inspector.inspect(url, policy.policyName());
        boolean match = result.status() == LinkCheckResult.Status.CONTENT_MATCH;
        int failures = match ? 0 : url.equals(policy.checkedUrl()) ? policy.failures() + 1 : 1;
        Instant now = Instant.now();
        Instant next = now.plus(Duration.ofDays(match ? 7 : Math.min(7, 1L << Math.min(failures - 1, 3))));
        jdbc.update("""
                UPDATE policy SET source_link_checked_url = ?, source_link_status = ?, source_link_checked_at = ?,
                    source_link_final_url = ?, source_link_http_status = ?, source_link_reason = ?,
                    source_link_failures = ?, source_link_next_check_at = ?,
                    source_link_published_usable = CASE WHEN coalesce(verified_source_url, source_url) = ?
                        THEN ? ELSE source_link_published_usable END WHERE id = ?
                """, url, result.status().name(), Timestamp.from(now), result.finalUrl(), result.httpStatus(),
                result.reason(), failures, Timestamp.from(next), url, match, id);
        // Separate commits finish before cache invalidation; API source and review metadata are preserved.
        cache.evictAll();
        return get(id);
    }

    public LinkView approve(long id, String url, String evidence, String reviewer) {
        return locked(() -> {
            if (url == null || evidence == null || evidence.trim().length() < 20 || evidence.length() > 4000) {
                throw new IllegalArgumentException("정책명·제공기관·지원 대상·신청 절차를 확인한 근거를 20자 이상 남겨 주세요.");
            }
            int changed = jdbc.update("""
                    UPDATE policy SET verified_source_url = source_link_checked_url, source_link_verified_at = ?,
                        source_link_reviewed_by = ?, source_link_review_evidence = ?, source_link_published_usable = TRUE
                    WHERE id = ? AND source_link_checked_url = ? AND source_link_status = 'CONTENT_MATCH'
                        AND source_link_checked_at >= ?
                    """, Timestamp.from(Instant.now()), reviewer, evidence.trim(), id, url.trim(),
                    Timestamp.from(Instant.now().minus(Duration.ofHours(24))));
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "24시간 이내에 본문 일치로 점검한 주소만 등록할 수 있습니다. 먼저 다시 점검해 주세요.");
            cache.evictAll();
            return get(id);
        });
    }

    public Map<String, Integer> checkDue(int limit) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("한 번에 1~50건만 점검할 수 있습니다.");
        return locked(() -> {
            List<Long> ids = jdbc.queryForList("""
                    SELECT id FROM policy WHERE lifecycle_status = 'ACTIVE'
                        AND (source_link_next_check_at IS NULL OR source_link_next_check_at <= now())
                    ORDER BY (verified_source_url IS NOT NULL) DESC, source_link_next_check_at NULLS FIRST, id LIMIT ?
                    """, Long.class, limit);
            for (long id : ids) inspectAndSave(id, null);
            return Map.of("checked", ids.size());
        });
    }

    private <T> T locked(Supplier<T> action) {
        // Session lock also excludes manual checks/approval on other backend instances.
        return jdbc.execute((ConnectionCallback<T>) connection -> {
            try (var lock = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                lock.setLong(1, LOCK_KEY);
                try (var result = lock.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 원문 점검이 진행 중입니다. 잠시 후 다시 시도해 주세요.");
                }
            }
            try {
                return action.get();
            } finally {
                try (var unlock = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    unlock.setLong(1, LOCK_KEY);
                    unlock.execute();
                }
            }
        });
    }

    private static LinkView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LinkView(rs.getLong("id"), rs.getString("name"), rs.getString("provider"),
                rs.getString("source_url"), rs.getString("verified_source_url"), instant(rs, "source_link_verified_at"),
                rs.getString("source_link_checked_url"), rs.getString("source_link_status"), instant(rs, "source_link_checked_at"),
                rs.getString("source_link_final_url"), (Integer) rs.getObject("source_link_http_status"), rs.getString("source_link_reason"),
                rs.getInt("source_link_failures"), instant(rs, "source_link_next_check_at"), rs.getString("source_link_reviewed_by"),
                rs.getString("source_link_review_evidence"));
    }

    private static Instant instant(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
}
