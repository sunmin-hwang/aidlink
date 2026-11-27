package io.aidlink.briefing.application;

import io.aidlink.user.infrastructure.jpa.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Profile("postgres")
public class BriefingDeliveryService {
    private final JdbcTemplate jdbc;
    private final AppUserJpaRepository users;
    private final PersonalBriefingService briefings;
    private final BriefingPdfRenderer pdf;
    private final BriefingMailGateway mail;
    private final TransactionTemplate transactions;
    public BriefingDeliveryService(JdbcTemplate jdbc,AppUserJpaRepository users,PersonalBriefingService briefings,
                                   BriefingPdfRenderer pdf,BriefingMailGateway mail,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.users=users; this.briefings=briefings; this.pdf=pdf; this.mail=mail;
        this.transactions=new TransactionTemplate(manager);
    }
    public record Delivery(UUID id,UUID briefingId,String status,Instant scheduledAt,Instant sentAt,
                           int attemptCount,String failureMessage) { }
    private record Claimed(UUID id,String email,PersonalBriefingService.Report report) { }

    @Transactional
    public Delivery schedule(String email,UUID reportId,UUID requestId,Instant requestedAt,boolean consent) {
        var user=users.findByEmailIgnoreCase(email).orElseThrow();
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user.getId());
        var report=briefings.get(email,reportId);
        if(report.content()==null) throw error(HttpStatus.CONFLICT,"완료된 브리핑만 발송할 수 있습니다.");
        if(!consent) throw error(HttpStatus.BAD_REQUEST,"이 브리핑의 이메일 수신에 동의해 주세요.");
        if(!user.isEmailVerified()) throw error(HttpStatus.CONFLICT,"수신 이메일을 먼저 확인해 주세요.");
        mail.requireReady(user.getEmail());
        var existing=jdbc.query("SELECT * FROM personal_briefing_email_delivery WHERE user_id=? AND request_id=?",this::map,user.getId(),requestId);
        if(!existing.isEmpty()) {
            var delivery=existing.getFirst();
            if(!delivery.briefingId().equals(reportId) || (requestedAt!=null && !delivery.scheduledAt().equals(requestedAt.truncatedTo(ChronoUnit.MICROS))))
                throw error(HttpStatus.CONFLICT,"같은 요청 번호에 다른 예약을 사용할 수 없습니다.");
            return delivery;
        }
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant scheduled=requestedAt==null?now:requestedAt.truncatedTo(ChronoUnit.MICROS);
        if(requestedAt!=null && (scheduled.isBefore(now.plusSeconds(5)) || scheduled.isAfter(now.plus(31,ChronoUnit.DAYS))))
            throw error(HttpStatus.BAD_REQUEST,"예약 시각은 5초 이후부터 31일 이내로 지정해 주세요.");
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO personal_briefing_email_delivery(id,user_id,briefing_id,request_id,recipient_email,
                consented_at,scheduled_at,status,created_at) VALUES (?,?,?,?,?,?,?,'SCHEDULED',?)
                """,id,user.getId(),reportId,requestId,user.getEmail(),Timestamp.from(now),Timestamp.from(scheduled),Timestamp.from(now));
        return new Delivery(id,reportId,"SCHEDULED",scheduled,null,0,null);
    }

    @Transactional(readOnly=true)
    public List<Delivery> list(String email) {
        if(!briefings.access(email).enabled()) throw error(HttpStatus.FORBIDDEN,"맞춤 브리핑 구독 권한이 필요합니다.");
        long userId=users.findByEmailIgnoreCase(email).orElseThrow().getId();
        return jdbc.query("SELECT * FROM personal_briefing_email_delivery WHERE user_id=? ORDER BY created_at DESC,id LIMIT 20",this::map,userId);
    }

    @Transactional
    public void cancel(String email,UUID id) {
        long userId=users.findByEmailIgnoreCase(email).orElseThrow().getId();
        int updated=jdbc.update("UPDATE personal_briefing_email_delivery SET status='CANCELLED' WHERE id=? AND user_id=? AND status='SCHEDULED'",id,userId);
        if(updated==0) throw error(HttpStatus.CONFLICT,"취소 가능한 본인 예약을 찾을 수 없습니다.");
    }

    public void dispatchDue() {
        // Never automatically resend a job whose SMTP outcome is uncertain, including a crash after acceptance.
        jdbc.update("""
                UPDATE personal_briefing_email_delivery SET status='UNKNOWN',failure_message='발송 결과를 확인하지 못했습니다. 수신함을 확인해 주세요.'
                WHERE status='SENDING' AND started_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                """);
        var ids=jdbc.queryForList("SELECT id FROM personal_briefing_email_delivery WHERE status='SCHEDULED' AND scheduled_at<=CURRENT_TIMESTAMP ORDER BY scheduled_at LIMIT 5",UUID.class);
        for(UUID id:ids) dispatch(id);
    }

    private void dispatch(UUID id) {
        Claimed claimed=transactions.execute(status -> claim(id));
        if(claimed==null) return;
        String state="SENT",failure=null;
        try {
            var bytes=pdf.render(claimed.report());
            mail.report(claimed.email(),claimed.report(),bytes,id);
        } catch(MailException exception) {
            state="UNKNOWN";
            failure="SMTP 전송 결과를 확정할 수 없습니다. 수신함을 먼저 확인해 주세요. 자동 재발송하지 않습니다.";
        } catch(RuntimeException exception) {
            state="FAILED";
            failure="PDF 또는 메일을 준비하지 못했습니다. 설정을 확인한 뒤 새 발송을 요청해 주세요.";
        }
        jdbc.update("UPDATE personal_briefing_email_delivery SET status=?,sent_at=?,failure_message=? WHERE id=? AND status='SENDING'",
                state,"SENT".equals(state)?Timestamp.from(Instant.now()):null,failure,id);
    }

    private Claimed claim(UUID id) {
        var rows=jdbc.query("SELECT * FROM personal_briefing_email_delivery WHERE id=? AND status='SCHEDULED' AND scheduled_at<=CURRENT_TIMESTAMP FOR UPDATE SKIP LOCKED", (rs,n)->new Object[]{rs.getLong("user_id"),rs.getObject("briefing_id",UUID.class),rs.getString("recipient_email")},id);
        if(rows.isEmpty()) return null;
        Object[] row=rows.getFirst();
        var user=users.findById((Long)row[0]).orElseThrow();
        try {
            if(!user.isEmailVerified() || !user.getEmail().equalsIgnoreCase((String)row[2])) throw error(HttpStatus.CONFLICT,"수신 주소가 변경되었거나 확인되지 않았습니다.");
            var report=briefings.get(user.getEmail(),(UUID)row[1]);
            mail.requireReady(user.getEmail());
            jdbc.update("UPDATE personal_briefing_email_delivery SET status='SENDING',attempt_count=1,started_at=CURRENT_TIMESTAMP WHERE id=?",id);
            return new Claimed(id,user.getEmail(),report);
        } catch(ResponseStatusException exception) {
            jdbc.update("UPDATE personal_briefing_email_delivery SET status='CANCELLED',failure_message='구독 권한 또는 수신 주소·메일 설정이 변경되어 발송을 취소했습니다.' WHERE id=?",id);
            return null;
        }
    }

    private Delivery map(java.sql.ResultSet rs,int index) throws java.sql.SQLException {
        var sent=rs.getTimestamp("sent_at");
        return new Delivery(rs.getObject("id",UUID.class),rs.getObject("briefing_id",UUID.class),rs.getString("status"),
                rs.getTimestamp("scheduled_at").toInstant(),sent==null?null:sent.toInstant(),rs.getInt("attempt_count"),rs.getString("failure_message"));
    }
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
