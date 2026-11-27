package io.aidlink.briefing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.user.application.UserProfileOperations;
import io.aidlink.user.application.UserProfileService;
import io.aidlink.user.infrastructure.jpa.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
@Profile("postgres")
@Transactional(readOnly = true)
public class PersonalBriefingService {
    private static final Logger log = LoggerFactory.getLogger(PersonalBriefingService.class);
    private final AppUserJpaRepository users;
    private final UserProfileJpaRepository profiles;
    private final UserProfileOperations profileService;
    private final BriefingGenerator generator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PersonalBriefingService(AppUserJpaRepository users, UserProfileJpaRepository profiles,
                                  UserProfileOperations profileService, BriefingGenerator generator,
                                  JdbcTemplate jdbc, ObjectMapper mapper) {
        this.users = users; this.profiles = profiles; this.profileService = profileService;
        this.generator = generator; this.jdbc = jdbc; this.mapper = mapper;
    }

    public record Access(boolean enabled, String source) { }
    public record Summary(UUID id, String status, Instant createdAt) { }
    public record Report(UUID id, String status, Instant createdAt, BriefingContent content, String failureMessage) { }

    public Access access(String email) { return new Access(enabled(user(email).getId()), "TEST_GRANT"); }

    @Transactional
    public Access setAccess(long userId, boolean enabled, String admin) {
        lockUser(userId);
        jdbc.update("""
                INSERT INTO briefing_test_access(user_id, enabled, updated_by, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET enabled=EXCLUDED.enabled,
                    updated_by=EXCLUDED.updated_by, updated_at=EXCLUDED.updated_at
                """, userId, enabled, admin, java.sql.Timestamp.from(Instant.now()));
        return new Access(enabled, "TEST_GRANT");
    }

    public List<Summary> list(String email) {
        long userId = requireAccess(email);
        return jdbc.query("SELECT id, status, created_at FROM personal_briefing WHERE user_id=? ORDER BY created_at DESC, id LIMIT 20",
                (rs, n) -> new Summary(rs.getObject("id", UUID.class), rs.getString("status"), rs.getTimestamp("created_at").toInstant()), userId);
    }

    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public Report get(String email, UUID id) {
        long userId = requireAccess(email);
        return jdbc.query("SELECT * FROM personal_briefing WHERE id=? AND user_id=?", (rs, n) ->
                new Report(id, rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                        readContent(rs.getString("report_payload")), rs.getString("failure_message")), id, userId)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "브리핑을 찾을 수 없습니다."));
    }

    @Transactional
    public Report create(String email) {
        var user = user(email);
        lockUser(user.getId()); // Serializes creation with grant/revocation and concurrent generation.
        requireAccess(email);
        var profile = profileService.get(email);
        if (!profiles.existsById(user.getId()) || profile.equals(UserProfileService.emptyProfile()))
            throw error(HttpStatus.CONFLICT, "프로필을 저장한 뒤 브리핑을 생성해 주세요.");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        UUID id = UUID.randomUUID();
        BriefingContent content = null;
        String failure = null;
        try { content = generator.generate(profile, now); }
        catch (RuntimeException exception) {
            // Persist a failed attempt without exposing profile values or infrastructure details.
            log.warn("Briefing generation failed: report={} type={}", id, exception.getClass().getSimpleName());
            failure = "브리핑 생성에 실패했습니다. 잠시 후 다시 생성해 주세요.";
        }
        String status = failure == null ? "COMPLETED" : "FAILED";
        jdbc.update("""
                INSERT INTO personal_briefing(id,user_id,status,created_at,profile_snapshot,report_payload,failure_message)
                VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, id, user.getId(), status, java.sql.Timestamp.from(now), json(profile),
                content == null ? null : json(content), failure);
        return new Report(id, status, now, content, failure);
    }

    private long requireAccess(String email) {
        long id = user(email).getId();
        if (!enabled(id)) throw error(HttpStatus.FORBIDDEN, "맞춤 브리핑 구독 권한이 필요합니다.");
        return id;
    }
    private boolean enabled(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM briefing_test_access WHERE user_id=? AND enabled)", Boolean.class, id));
    }
    private AppUserEntity user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
    private void lockUser(long id) {
        if (jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
            throw error(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("브리핑을 저장하지 못했습니다.", exception); }
    }
    private BriefingContent readContent(String value) {
        if (value == null) return null;
        try { return mapper.readValue(value, BriefingContent.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("브리핑을 읽지 못했습니다.", exception); }
    }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
