package io.aidlink.policy.period;

import io.aidlink.policy.application.PublicPolicyCache;
import io.aidlink.policy.application.PublicPolicyLink;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@Profile("postgres")
public class PolicyPeriodService {
    private final JdbcTemplate jdbc;
    private final PolicyJpaRepository policies;
    private final PublicPolicyCache cache;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public PolicyPeriodService(JdbcTemplate jdbc, PolicyJpaRepository policies, PublicPolicyCache cache) {
        this.jdbc = jdbc; this.policies = policies; this.cache = cache;
    }

    public record View(long policyId, String policyName, String lifecycleStatus, LocalDate start, LocalDate end,
                       PublicApplicationPeriod period, String verifiedText, String verifiedSourceUrl, String reviewer, String evidence) { }
    public record SearchPage(List<Map<String, Object>> content, long totalElements, int page, int totalPages) { }
    public record Preview(String fingerprint, long total, Map<String, Long> sourceKinds, long expired,
                          long verifiedPreserved, boolean dryRun) { }

    @Transactional(readOnly = true)
    public View get(long id) { return view(required(id)); }

    public SearchPage search(String query, int requestedPage) {
        if (requestedPage < 0) throw new IllegalArgumentException("페이지를 확인해 주세요.");
        String q = query == null ? "" : query.trim();
        String filter = " WHERE (? = '' OR name ILIKE ? OR external_id ILIKE ? OR id::text = ?)";
        Object[] args = {q, "%" + q + "%", "%" + q + "%", q};
        long total = jdbc.queryForObject("SELECT count(*) FROM policy" + filter, Long.class, args);
        int pages = (int) ((total + 19) / 20);
        int page = Math.min(requestedPage, Math.max(0, pages - 1));
        return new SearchPage(jdbc.queryForList("SELECT id, name, provider, lifecycle_status FROM policy" + filter
                + " ORDER BY id LIMIT 20 OFFSET " + page * 20L, args), total, page, pages);
    }

    @Transactional
    public View verify(long id, String periodText, String sourceUrl, String evidence, String expectedSourceText, String reviewer) {
        PolicyEntity policy = policies.findOneForPeriodUpdate(id).orElseThrow(() -> notFound());
        if (sourceUrl == null || sourceUrl.length() > 4000 || PublicPolicyLink.usableUrl(sourceUrl).isBlank()
                || !sourceUrl.equals(policy.getVerifiedSourceUrl())) {
            throw new IllegalArgumentException("먼저 원문 검수에서 등록한 제공기관 주소를 입력해 주세요.");
        }
        if (!Objects.equals(expectedSourceText, policy.getSchedule().raw())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "수집 기한이 변경됐습니다. 정책을 다시 조회해 검수해 주세요.");
        }
        if (periodText == null || periodText.length() > 2000 || evidence == null || evidence.trim().length() < 20 || evidence.length() > 4000) {
            throw new IllegalArgumentException("확정 신청기간과 20자 이상의 검수 근거를 입력해 주세요.");
        }
        policy.verifyPeriod(periodText.trim(), sourceUrl, evidence.trim(), reviewer);
        policies.flush();
        jdbc.update("""
                UPDATE policy SET application_period_review_history = application_period_review_history || jsonb_build_array(
                    jsonb_build_object('periodText', ?::text, 'sourceUrl', ?::text, 'sourceText', ?::text,
                        'evidence', ?::text, 'reviewer', ?::text, 'verifiedAt', CURRENT_TIMESTAMP)) WHERE id = ?
                """, periodText.trim(), sourceUrl, expectedSourceText, evidence.trim(), reviewer, id);
        invalidateAfterCommit();
        return view(policy);
    }

    @Transactional(readOnly = true)
    public Preview preview() { return summarize(snapshots(false), true); }

    @Transactional
    public Preview apply(String expectedFingerprint) {
        // Lock the same rows read by the dry-run so collection/review cannot interleave with application.
        List<Snapshot> rows = snapshots(true);
        Preview preview = summarize(rows, false);
        if (!Objects.equals(expectedFingerprint, preview.fingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "미리보기 이후 데이터가 변경됐습니다. 다시 미리보기 해주세요.");
        }
        LocalDateTime now = LocalDateTime.now(SEOUL);
        List<Object[]> updates = new ArrayList<>();
        for (Snapshot row : rows) {
            ApplicationWindow source = row.source();
            ApplicationWindow effective = row.effective();
            updates.add(new Object[]{ApplicationPeriodParser.sourceText(row.method), source.start(), source.end(), source.startTime(), source.endTime(),
                    effective.kind().name(), effective.start(), effective.end(), effective.startTime(), effective.endTime(),
                    effective.expired(now) ? "EXPIRED" : "ACTIVE", row.id});
        }
        jdbc.batchUpdate("""
                UPDATE policy SET application_period_raw = ?, source_application_start = ?, source_application_end = ?,
                    source_application_start_time = ?, source_application_end_time = ?, application_period_kind = ?,
                    application_start = ?, application_end = ?, application_start_time = ?, application_end_time = ?,
                    lifecycle_status = CASE WHEN lifecycle_status IN ('ACTIVE','EXPIRED') THEN ? ELSE lifecycle_status END,
                    updated_at = now() WHERE id = ?
                """, updates);
        invalidateAfterCommit();
        return preview;
    }

    @Transactional
    public int refreshLifecycle() {
        int changed = jdbc.update("""
                UPDATE policy SET lifecycle_status = CASE WHEN application_end < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
                    OR (application_end = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AND application_end_time < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::time)
                    THEN 'EXPIRED' ELSE 'ACTIVE' END, updated_at = now()
                WHERE lifecycle_status IN ('ACTIVE','EXPIRED') AND lifecycle_status <> CASE WHEN application_end < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
                    OR (application_end = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AND application_end_time < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::time)
                    THEN 'EXPIRED' ELSE 'ACTIVE' END
                """);
        if (changed > 0) invalidateAfterCommit();
        return changed;
    }

    private List<Snapshot> snapshots(boolean lock) {
        return jdbc.query("""
                SELECT id, application_method, application_start, application_end, source_application_start, source_application_end,
                    verified_application_start, verified_application_end, verified_application_start_time,
                    verified_application_end_time, application_period_verified_at, lifecycle_status
                FROM policy ORDER BY id
                """ + (lock ? " FOR UPDATE" : ""), (rs, n) -> new Snapshot(rs.getLong("id"), rs.getString("application_method"),
                rs.getObject("application_start", LocalDate.class), rs.getObject("application_end", LocalDate.class),
                rs.getObject("source_application_start", LocalDate.class), rs.getObject("source_application_end", LocalDate.class),
                rs.getObject("verified_application_start", LocalDate.class), rs.getObject("verified_application_end", LocalDate.class),
                rs.getObject("verified_application_start_time", LocalTime.class), rs.getObject("verified_application_end_time", LocalTime.class),
                rs.getTimestamp("application_period_verified_at") == null ? null : rs.getTimestamp("application_period_verified_at").toInstant(), rs.getString("lifecycle_status")));
    }

    private record Snapshot(long id, String method, LocalDate start, LocalDate end, LocalDate sourceStart, LocalDate sourceEnd,
                            LocalDate verifiedStart, LocalDate verifiedEnd, LocalTime verifiedStartTime, LocalTime verifiedEndTime, Instant verifiedAt, String status) {
        ApplicationWindow source() { return ApplicationPeriodParser.fromSource(method,
                verifiedAt == null ? start : sourceStart, verifiedAt == null ? end : sourceEnd); }
        ApplicationWindow effective() { return verifiedAt == null ? source() : new ApplicationWindow(ApplicationWindow.Kind.FIXED, verifiedStart, verifiedEnd, verifiedStartTime, verifiedEndTime); }
    }

    private Preview summarize(List<Snapshot> rows, boolean dryRun) {
        Map<String, Long> kinds = new TreeMap<>();
        long expired = 0, verified = 0;
        LocalDateTime now = LocalDateTime.now(SEOUL);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Snapshot row : rows) {
                digest.update((row.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                kinds.merge(row.source().kind().name(), 1L, Long::sum);
                if (row.effective().expired(now) && !Set.of("ARCHIVED", "SOURCE_REMOVED").contains(row.status)) expired++;
                if (row.verifiedAt != null) verified++;
            }
            return new Preview(HexFormat.of().formatHex(digest.digest()), rows.size(), kinds, expired, verified, dryRun);
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private View view(PolicyEntity policy) {
        return new View(policy.getId(), policy.getName(), policy.getLifecycleStatus().name(), policy.getApplicationStart(), policy.getApplicationEnd(),
                policy.getSchedule().publicView(), policy.getSchedule().verifiedText(), policy.getVerifiedSourceUrl(), policy.getSchedule().reviewer(), policy.getSchedule().evidence());
    }
    private PolicyEntity required(long id) { return policies.findById(id).orElseThrow(() -> notFound()); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "정책을 찾을 수 없습니다."); }
    private void invalidateAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evictAll(); }
        });
    }
}
