package io.aidlink.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.briefing.application.*;
import io.aidlink.user.application.UserProfileOperations;
import io.aidlink.user.domain.UserProfile;
import io.aidlink.user.infrastructure.jpa.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aidlink.redis.enabled=false", "aidlink.briefing.mail.enabled=true", "aidlink.briefing.mail.scheduling-enabled=false",
        "aidlink.briefing.mail.from=sender@aidlink.test", "aidlink.briefing.mail.allowed-recipients=mail-briefing-test-owner@example.test,mail-briefing-test-other@example.test",
        "spring.mail.host=127.0.0.1","spring.mail.port=11025","spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false","spring.mail.properties.mail.smtp.starttls.required=false"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named="AIDLINK_BRIEFING_MAIL_TEST",matches="true")
class BriefingMailPostgresIntegrationTest {
    @Autowired AppUserJpaRepository users;
    @Autowired UserProfileOperations profiles;
    @Autowired PersonalBriefingService briefings;
    @Autowired BriefingDeliveryService deliveries;
    @Autowired BriefingEmailVerificationService verification;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoSpyBean BriefingMailGateway gateway;
    @MockitoSpyBean BriefingPdfRenderer pdf;
    private static final String OWNER="mail-briefing-test-owner@example.test",OTHER="mail-briefing-test-other@example.test";
    private final AtomicReference<String> token=new AtomicReference<>();
    private UUID reportId;
    private long ownerId;
    private RestClient mailpit=RestClient.create("http://127.0.0.1:18025");

    @BeforeEach void setup() throws Exception {
        clean();
        mailpit.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
        ownerId=users.saveAndFlush(new AppUserEntity(OWNER,"unused","ROLE_USER",Instant.now())).getId();
        var other=users.saveAndFlush(new AppUserEntity(OTHER,"unused","ROLE_USER",Instant.now()));
        profiles.update(OWNER,mapper.readValue("{\"birthDate\":\"1998-01-01\",\"regionCode\":\"SEOUL\"}",UserProfile.class));
        briefings.setAccess(ownerId,true,"test-admin"); briefings.setAccess(other.getId(),true,"test-admin");
        reportId=briefings.create(OWNER).id();
        doAnswer(invocation->{ token.set(invocation.getArgument(1)); invocation.callRealMethod(); return null; }).when(gateway).verification(anyString(),anyString());
    }
    @AfterEach void clean() { jdbc.update("DELETE FROM app_user WHERE email LIKE 'mail-briefing-test-%'"); }
    private void verifyOwner() { verification.request(OWNER); verification.confirm(OWNER,token.get()); }
    private BriefingDeliveryService.Delivery immediate() { return deliveries.schedule(OWNER,reportId,UUID.randomUUID(),null,true); }

    @Test void verificationRequiresPossessionExpiresAndCannotBeReused() {
        verification.request(OWNER);
        assertThat(verification.status(OWNER).verified()).isFalse();
        String code=token.get();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM email_verification_token WHERE user_id=?",String.class,ownerId)).doesNotContain(code);
        assertThatThrownBy(()->verification.request(OWNER)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(()->verification.confirm(OTHER,code)).isInstanceOf(ResponseStatusException.class);
        verification.confirm(OWNER,code);
        assertThat(verification.status(OWNER).verified()).isTrue();
        assertThatThrownBy(()->verification.confirm(OWNER,code)).isInstanceOf(ResponseStatusException.class);
        // Create a new token and expire it without breaking its DB time ordering constraint.
        verification.request(OWNER); String expired=token.get();
        jdbc.update("UPDATE email_verification_token SET created_at=CURRENT_TIMESTAMP-INTERVAL '30 minutes',expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE user_id=? AND used_at IS NULL",ownerId);
        assertThatThrownBy(()->verification.confirm(OWNER,expired)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsMissingConsentUnverifiedFreeAndOtherOwnerAccess() throws Exception {
        assertThatThrownBy(()->deliveries.schedule(OWNER,reportId,UUID.randomUUID(),null,true)).isInstanceOf(ResponseStatusException.class);
        verifyOwner();
        assertThatThrownBy(()->deliveries.schedule(OWNER,reportId,UUID.randomUUID(),null,false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->deliveries.schedule(OTHER,reportId,UUID.randomUUID(),null,true)).isInstanceOf(ResponseStatusException.class);
        mvc.perform(post("/api/v1/me/briefings/"+reportId+"/deliveries").with(user(OWNER)).contentType("application/json")
                .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"consent\":true}"))
                .andExpect(status().isForbidden());
        briefings.setAccess(ownerId,false,"test-admin");
        mvc.perform(get("/api/v1/me/briefing-email").with(user(OWNER))).andExpect(status().isForbidden());
    }

    @Test void immediateSendUsesIdempotencyAndConcurrentWorkersOnlyClaimOnce() throws Exception {
        verifyOwner(); UUID requestId=UUID.randomUUID();
        var first=deliveries.schedule(OWNER,reportId,requestId,null,true);
        assertThat(deliveries.schedule(OWNER,reportId,requestId,null,true).id()).isEqualTo(first.id());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(deliveries::dispatchDue); var b=pool.submit(deliveries::dispatchDue);
            a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        }
        deliveries.dispatchDue();
        assertThat(deliveries.list(OWNER)).hasSize(1);
        assertThat(deliveries.list(OWNER).getFirst().status()).isEqualTo("SENT");
        assertThat(deliveries.list(OWNER).getFirst().attemptCount()).isEqualTo(1);
        verify(gateway,times(1)).report(eq(OWNER),any(),any(),eq(first.id()));
        var messages=mailpit.get().uri("/api/v1/messages").retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(messages.path("total").asInt()).isEqualTo(2); // Confirmation + one PDF email.
    }

    @Test void futureScheduleWaitsAndCancellationOrRevocationPreventsSend() {
        verifyOwner();
        var future=deliveries.schedule(OWNER,reportId,UUID.randomUUID(),Instant.now().plusSeconds(30),true);
        deliveries.dispatchDue(); assertThat(deliveries.list(OWNER).getFirst().status()).isEqualTo("SCHEDULED");
        assertThatThrownBy(()->deliveries.cancel(OTHER,future.id())).isInstanceOf(ResponseStatusException.class);
        deliveries.cancel(OWNER,future.id());
        var revoked=immediate(); briefings.setAccess(ownerId,false,"test-admin"); deliveries.dispatchDue();
        assertThat(jdbc.queryForObject("SELECT status FROM personal_briefing_email_delivery WHERE id=?",String.class,revoked.id())).isEqualTo("CANCELLED");
        verify(gateway,never()).report(anyString(),any(),any(),any());
    }

    @Test void preparationFailureAndAmbiguousSmtpAreRecordedWithoutAutomaticRetry() {
        verifyOwner();
        var failed=immediate();
        doThrow(new IllegalStateException("private rendering failure")).doCallRealMethod().when(pdf).render(any());
        deliveries.dispatchDue(); assertThat(deliveries.list(OWNER).getFirst().status()).isEqualTo("FAILED");
        var uncertain=immediate();
        doThrow(new MailSendException("private transport failure")).when(gateway).report(anyString(),any(),any(),any());
        deliveries.dispatchDue(); deliveries.dispatchDue();
        assertThat(jdbc.queryForObject("SELECT status FROM personal_briefing_email_delivery WHERE id=?",String.class,uncertain.id())).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT failure_message FROM personal_briefing_email_delivery WHERE id=?",String.class,failed.id())).doesNotContain("private");
        verify(gateway,times(1)).report(anyString(),any(),any(),eq(uncertain.id()));
    }

    @Test void interruptedSendingBecomesUnknownAndIsNeverAutomaticallyResent() {
        verifyOwner(); var job=immediate();
        jdbc.update("UPDATE personal_briefing_email_delivery SET status='SENDING',attempt_count=1,started_at=CURRENT_TIMESTAMP-INTERVAL '11 minutes' WHERE id=?",job.id());
        deliveries.dispatchDue(); deliveries.dispatchDue();
        assertThat(deliveries.list(OWNER).getFirst().status()).isEqualTo("UNKNOWN");
        verify(gateway,never()).report(anyString(),any(),any(),any());
    }
}
