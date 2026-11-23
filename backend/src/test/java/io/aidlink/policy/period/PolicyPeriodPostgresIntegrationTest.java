package io.aidlink.policy.period;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.application.Gov24PolicyNormalizer;
import io.aidlink.collector.domain.*;
import io.aidlink.collector.infrastructure.jpa.JpaNormalizedPolicyStore;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aidlink.redis.enabled=false", "aidlink.links.scheduling-enabled=false", "aidlink.collector.gov24.scheduling-enabled=false", "aidlink.periods.lifecycle-refresh-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named="AIDLINK_PERIOD_DB_TEST", matches="true")
class PolicyPeriodPostgresIntegrationTest {
    @Autowired PolicyPeriodService service;
    @Autowired PolicyJpaRepository policies;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Gov24PolicyNormalizer normalizer;
    @Autowired JpaNormalizedPolicyStore collector;
    @Autowired io.aidlink.user.infrastructure.jpa.AppUserJpaRepository users;
    @Autowired io.aidlink.policy.application.PolicyBookmarkOperations bookmarks;
    private long id;
    private static final String URL="https://provider.or.kr/period";
    private static final String API="2099.10.07~2099.10.15";
    private static final String REVIEW="2099년 10월 7일(수) 10:00 ~ 2099년 10월 20일(화)";
    private static final String EVIDENCE="제공기관의 신청 접수 기간 연장 공고 본문을 직접 확인했고 종료일 연장과 시작 시각을 대조했습니다.";

    @BeforeEach void setUp() {
        jdbc.update("DELETE FROM policy WHERE external_id LIKE 'PERIOD-TEST:%'");
        PolicyEntity policy = entity("MAIN", "신청기한: " + API);
        ReflectionTestUtils.setField(policy,"verifiedSourceUrl",URL);
        id=policies.saveAndFlush(policy).getId();
    }
    private PolicyEntity entity(String key, String method) {
        return new PolicyEntity("PERIOD-TEST:"+key,"기간검증 "+key,"시험기관","교육","SEOUL","설명","대상","지원",null,null,method,"서류","https://apply.or.kr",URL,PolicyAnalysisStatus.RAW,null);
    }
    @Test void reviewPreservesApiAndSurvivesRealCollectorUpdatesAndBackfill() throws Exception {
        service.verify(id,REVIEW,URL,EVIDENCE,API,"reviewer");
        var view=service.get(id);
        assertThat(view.end()).isEqualTo(LocalDate.of(2099,10,20));
        assertThat(view.period().startTime()).isEqualTo(LocalTime.of(10,0));
        assertThat(view.period().endTime()).isNull();
        assertThat(jdbc.queryForObject("SELECT application_start_time::text FROM policy WHERE id=?",String.class,id)).isEqualTo("10:00:00");
        assertThat(jdbc.queryForObject("SELECT verified_application_start_time::text FROM policy WHERE id=?",String.class,id)).isEqualTo("10:00:00");
        assertThat(view.period().sourceText()).isEqualTo(API);
        var raw=mapper.readTree("""
                {"서비스명":"기간검증 MAIN","소관기관명":"시험기관","신청기한":"2099.10.07~2099.10.15"}
                """);
        var normalized=normalizer.normalize(new RawPolicyDocument(PolicySource.GOV24,"TEST",raw));
        // Preserve the fixture external identifier while exercising the production upsert.
        collector.upsert(new NormalizedPolicy("PERIOD-TEST:MAIN",PolicySource.GOV24,"PERIOD-MAIN",normalized.name(),normalized.provider(),normalized.category(),normalized.regionCode(),normalized.description(),normalized.targetDescription(),normalized.supportContent(),normalized.applicationStart(),normalized.applicationEnd(),normalized.applicationMethod(),normalized.requiredDocuments(),normalized.applicationUrl(),normalized.sourceUrl(),normalized.analysisStatus(),normalized.sourceUpdatedAt()),CollectionChangeType.UPDATED,Instant.now());
        assertThat(service.get(id).end()).isEqualTo(LocalDate.of(2099,10,20));
        assertThat(service.get(id).period().reviewRequired()).isFalse();
        var preview=service.preview();
        assertThat(preview.verifiedPreserved()).isPositive();
        service.apply(preview.fingerprint());
        assertThat(service.get(id).end()).isEqualTo(LocalDate.of(2099,10,20));
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(application_period_review_history) FROM policy WHERE id=?",Integer.class,id)).isEqualTo(1);
        // A later, different API schedule is retained as source data and requires re-review.
        var collected=policies.findById(id).orElseThrow();
        collected.update("기간검증 MAIN","시험기관","교육","SEOUL","설명","대상","지원",null,null,"신청기한: 2099.10.07~2099.10.25","서류","https://apply.or.kr",URL,PolicyAnalysisStatus.RAW,null);
        policies.saveAndFlush(collected);
        assertThat(service.get(id).end()).isEqualTo(LocalDate.of(2099,10,20));
        assertThat(service.get(id).period().reviewRequired()).isTrue();
    }
    @Test void dryRunIsReadOnlyAndRejectsStaleFingerprint() {
        jdbc.update("UPDATE policy SET application_start=NULL,application_end=NULL,application_period_kind='UNKNOWN' WHERE id=?",id);
        var preview=service.preview();
        assertThat(service.get(id).start()).isNull();
        jdbc.update("UPDATE policy SET application_method='신청기한: 접수기관 별 상이' WHERE id=?",id);
        assertThatThrownBy(()->service.apply(preview.fingerprint())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.get(id).start()).isNull();
        service.apply(service.preview().fingerprint());
        assertThat(service.get(id).period().kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN);
    }
    @Test void unknownIsExcludedFromOpenFilterAndExpiredDetailRemainsReadable() throws Exception {
        var unknown=policies.saveAndFlush(entity("UNKNOWN","신청기한: 기관별 상이"));
        var always=policies.saveAndFlush(entity("ALWAYS","신청기한: 상시신청"));
        var expired=policies.saveAndFlush(entity("EXPIRED","신청기한: 2000.10.07~2000.10.20"));
        service.apply(service.preview().fingerprint());
        mvc.perform(get("/api/v1/policies/search").param("q","기간검증").param("openOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(always.getId()));
        mvc.perform(get("/api/v1/policies/"+expired.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus").value("EXPIRED"));
        assertThat(service.get(unknown.getId()).period().kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN);
        jdbc.update("UPDATE policy SET lifecycle_status='ACTIVE' WHERE id=?",expired.getId());
        assertThat(service.refreshLifecycle()).isPositive();
        assertThat(service.get(expired.getId()).lifecycleStatus()).isEqualTo("EXPIRED");
    }
    @Test void monthlyGuidanceSqlMatchesJavaForEveryMonthWithoutChangingConfirmedDates() {
        for (String text : new String[]{"매년 1월", "매년도 1~2월", "매년 11월~2월 중", "매년 1월 신청",
                "매년 0월", "매년 1~13월", "매년 1월 초", "매년 연초", "매년 1월(모집기간 별도)",
                "신청 불필요", "별도의 신청절차가 없음", "기존 대상자는 신청 불필요, 신규는 1월 신청", "접수기관 별 상이", ""}) {
            for (int month=1; month<=12; month++) {
                var date=LocalDate.of(2026,month,1);
                assertThat(jdbc.queryForObject("SELECT policy_application_guidance_open(?,?::date)",Boolean.class,text,date))
                        .as("%s at %s",text,date).isEqualTo(ApplicationAvailability.guidanceOpen(text,date));
            }
        }
        assertThat(jdbc.queryForObject("SELECT policy_application_available_at('FIXED',NULL,'2025-12-31',NULL,NULL,'신청불필요','2026-01-01 00:00')",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT policy_application_available_at('FIXED','2026-01-01','2026-01-31','10:00',NULL,'매년 1월','2026-01-01 09:59')",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT policy_application_available_at('UNKNOWN',NULL,NULL,NULL,NULL,'매년 1월',('2025-12-31 15:00:00+00'::timestamptz AT TIME ZONE 'Asia/Seoul'))",Boolean.class)).isTrue();
    }

    @Test void openFilterIncludesNoApplicationAndThisMonthWhileKeepingPaginationAndExclusions() throws Exception {
        int month=LocalDate.now(ZoneId.of("Asia/Seoul")).getMonthValue();
        var noApplication=policies.saveAndFlush(entity("NO-APPLICATION","신청기한: 신청 불필요"));
        var recurring=policies.saveAndFlush(entity("RECURRING","신청기한: 매년 "+month+"월"));
        var always=policies.saveAndFlush(entity("AVAILABLE-ALWAYS","신청기한: 상시신청"));
        policies.saveAndFlush(entity("OTHER-MONTH","신청기한: 매년 "+(month%12+1)+"월"));
        policies.saveAndFlush(entity("AGENCY","신청기한: 접수기관 별 상이"));
        var review=policies.saveAndFlush(entity("RE-REVIEW","신청기한: 신청불필요"));
        jdbc.update("UPDATE policy SET application_period_review_required=true WHERE id=?",review.getId());
        var archived=policies.saveAndFlush(entity("ARCHIVED","신청기한: 신청불필요"));
        archived.archive();
        policies.saveAndFlush(archived);
        mvc.perform(get("/api/v1/policies/search").param("q","기간검증").param("openOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id",org.hamcrest.Matchers.containsInAnyOrder(
                        noApplication.getId().intValue(),recurring.getId().intValue(),always.getId().intValue())));
        mvc.perform(get("/api/v1/policies/search").param("q","기간검증").param("openOnly","true").param("size","1").param("page","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
        assertThat(service.get(recurring.getId()).period().kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN);
        assertThat(service.get(recurring.getId()).start()).isNull();
    }

    @Test void adminCsrfProvenanceAndSourceVersionAreRequired() throws Exception {
        String path="/api/v1/admin/policies/periods/"+id+"/verify";
        String body=mapper.writeValueAsString(new io.aidlink.interfaces.api.AdminPolicyPeriodController.Review(REVIEW,URL,EVIDENCE,API));
        mvc.perform(get("/api/v1/admin/policies/periods/backfill/preview")).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user("member").roles("USER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("admin").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertThatThrownBy(()->service.verify(id,REVIEW,URL,EVIDENCE,"stale","reviewer")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.verify(id,REVIEW,"https://unverified.or.kr",EVIDENCE,API,"reviewer")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void expiredBookmarksRemainInThePaginatedListWithAnEndedPeriod() throws Exception {
        String email="period-bookmark@example.invalid";
        var account=users.findByEmailIgnoreCase(email).orElseGet(()->users.saveAndFlush(
                new io.aidlink.user.infrastructure.jpa.AppUserEntity(email,"unused-test-hash","ROLE_USER",Instant.now())));
        var expired=policies.saveAndFlush(entity("BOOKMARK-EXPIRED","신청기한: 2000.10.07~2000.10.20"));
        bookmarks.add(account.getEmail(),expired.getId());
        mvc.perform(get("/api/v1/me/bookmarks").with(user(email).roles("USER")).param("page","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lifecycleStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.content[0].applicationEnd").value("2000-10-20"));
    }
}
