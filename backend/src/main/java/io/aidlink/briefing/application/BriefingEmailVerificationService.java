package io.aidlink.briefing.application;

import io.aidlink.user.infrastructure.jpa.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@Profile("postgres")
public class BriefingEmailVerificationService {
    private final AppUserJpaRepository users;
    private final EmailVerificationTokenJpaRepository tokens;
    private final PersonalBriefingService briefings;
    private final BriefingMailGateway mail;
    private final JdbcTemplate jdbc;
    public BriefingEmailVerificationService(AppUserJpaRepository users,EmailVerificationTokenJpaRepository tokens,
                                           PersonalBriefingService briefings,BriefingMailGateway mail,JdbcTemplate jdbc) {
        this.users=users; this.tokens=tokens; this.briefings=briefings; this.mail=mail; this.jdbc=jdbc;
    }
    public record Status(String email,boolean verified,boolean available) { }
    @Transactional(readOnly=true)
    public Status status(String email) { var user=requireUser(email); return new Status(user.getEmail(),user.isEmailVerified(),mail.available(user.getEmail())); }
    @Transactional
    public void request(String email) {
        var user=lockedUser(email);
        mail.requireReady(user.getEmail());
        Instant now=Instant.now();
        var active=tokens.findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId());
        if(active.stream().anyMatch(t->t.getCreatedAt().isAfter(now.minusSeconds(60))))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"확인 메일은 1분 뒤 다시 요청해 주세요.");
        active.forEach(t->t.revoke(now));
        byte[] bytes=new byte[24]; new SecureRandom().nextBytes(bytes);
        String token=HexFormat.of().formatHex(bytes);
        tokens.save(new EmailVerificationTokenEntity(user,hash(token),now.plusSeconds(900),now));
        mail.verification(user.getEmail(),token);
    }
    @Transactional
    public void confirm(String email,String token) {
        var user=lockedUser(email);
        if(token==null || !token.matches("[a-f0-9]{48}")) throw invalid();
        var entity=tokens.findByTokenHash(hash(token)).orElseThrow(this::invalid);
        Instant now=Instant.now();
        if(!entity.getUser().getId().equals(user.getId()) || !entity.isUsableAt(now)) throw invalid();
        entity.markUsed(now); user.markEmailVerified(now);
    }
    private AppUserEntity requireUser(String email) {
        if(!briefings.access(email).enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"맞춤 브리핑 구독 권한이 필요합니다.");
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }
    private AppUserEntity lockedUser(String email) {
        var user=requireUser(email);
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user.getId());
        return user;
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"확인 코드가 올바르지 않거나 만료되었습니다."); }
}
