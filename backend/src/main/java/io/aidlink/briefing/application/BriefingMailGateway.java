package io.aidlink.briefing.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Profile("postgres")
public class BriefingMailGateway {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    private final Set<String> allowed;
    public BriefingMailGateway(JavaMailSender sender,
                              @Value("${aidlink.briefing.mail.enabled:false}") boolean enabled,
                              @Value("${aidlink.briefing.mail.from:}") String from,
                              @Value("${aidlink.briefing.mail.allowed-recipients:}") String recipients) {
        this.sender=sender; this.enabled=enabled; this.from=from;
        this.allowed=new HashSet<>();
        for(String value:recipients.split(",")) if(!value.isBlank()) allowed.add(value.trim().toLowerCase(Locale.ROOT));
    }
    public void requireReady(String recipient) {
        if(!enabled || from.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 발송 설정이 아직 준비되지 않았습니다.");
        if(!allowed.contains(recipient.toLowerCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"확인된 테스트 수신 주소에서만 발송을 시험할 수 있습니다.");
    }
    public boolean available(String recipient) { return enabled && !from.isBlank() && allowed.contains(recipient.toLowerCase(Locale.ROOT)); }
    public void verification(String recipient,String token) {
        requireReady(recipient);
        try {
            var message=sender.createMimeMessage();
            var helper=new MimeMessageHelper(message,false,StandardCharsets.UTF_8.name());
            helper.setFrom(from); helper.setTo(recipient); helper.setSubject("[AIDLINK] 브리핑 수신 이메일 확인");
            helper.setText("브리핑 화면의 이메일 확인란에 아래 확인 코드를 입력해 주세요.\n\n"+token+"\n\n15분 동안 유효하며 한 번만 사용할 수 있습니다. 요청하지 않았다면 무시해 주세요.");
            sender.send(message);
        } catch(jakarta.mail.MessagingException | org.springframework.mail.MailException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"이메일 확인 메일을 보내지 못했습니다. 잠시 후 다시 요청해 주세요.");
        }
    }
    public void report(String recipient,PersonalBriefingService.Report report,byte[] pdf,UUID deliveryId) {
        requireReady(recipient);
        try {
            var message=sender.createMimeMessage();
            var helper=new MimeMessageHelper(message,true,StandardCharsets.UTF_8.name());
            helper.setFrom(from); helper.setTo(recipient);
            helper.setSubject("[AIDLINK] "+report.content().evaluationDate()+" 맞춤 정책 브리핑");
            helper.setText("요청하신 맞춤 정책 브리핑 PDF를 첨부했습니다.\n"+
                    "생성 당시 저장한 프로필과 정책 기준이며 현재의 자격이나 신청 가능 여부를 확정하지 않습니다.\n"+
                    "최종 자격과 일정은 PDF의 공식 원문에서 확인해 주세요.\n\n"+
                    "이 메일은 브리핑 화면에서 수신에 동의하여 요청한 1회 발송입니다.\n"+
                    "예약 취소와 발송 이력은 로그인 후 정책 브리핑 탭에서 확인할 수 있습니다.\n\n발송 요청: "+deliveryId);
            helper.addAttachment("aidlink-briefing-"+report.id()+".pdf",new ByteArrayResource(pdf),"application/pdf");
            message.setHeader("Message-ID","<"+deliveryId+"@aidlink.local>");
            sender.send(message);
        } catch(jakarta.mail.MessagingException exception) { throw new IllegalStateException("브리핑 메일을 준비하지 못했습니다.",exception); }
    }
}
