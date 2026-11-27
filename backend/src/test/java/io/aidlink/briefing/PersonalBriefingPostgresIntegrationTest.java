package io.aidlink.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.briefing.application.*;
import io.aidlink.user.application.*;
import io.aidlink.user.infrastructure.jpa.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"aidlink.redis.enabled=false", "aidlink.links.scheduling-enabled=false", "aidlink.collector.gov24.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named="AIDLINK_BRIEFING_DB_TEST", matches="true")
@Transactional
class PersonalBriefingPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserJpaRepository users;
    @Autowired UserProfileOperations profiles;
    @Autowired PersonalBriefingService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean BriefingGenerator generator;
    long ownerId;
    static final String OWNER = "briefing-owner@example.test";
    static final String OTHER = "briefing-other@example.test";

    @BeforeEach void setup() throws Exception {
        jdbc.update("UPDATE policy SET lifecycle_status='ACTIVE',archived_at=NULL,application_end=CURRENT_DATE+30 WHERE external_id LIKE 'SAMPLE-%'");
        ownerId = users.saveAndFlush(new AppUserEntity(OWNER, "unused", "ROLE_USER", Instant.now())).getId();
        users.saveAndFlush(new AppUserEntity(OTHER, "unused", "ROLE_USER", Instant.now()));
        profiles.update(OWNER, mapper.readValue("{\"birthDate\":\"1998-01-01\",\"regionCode\":\"SEOUL\"}", io.aidlink.user.domain.UserProfile.class));
    }

    @Test void freeAnonymousAndAdminBoundaries() throws Exception {
        mvc.perform(get("/api/v1/policies/search")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/briefings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/briefings").with(user(OWNER)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/briefing-access").with(user(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/api/v1/admin/briefing-access/"+ownerId).with(user(OWNER)).with(csrf())
                .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/briefing-access/"+ownerId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/briefing-access/"+ownerId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("TEST_GRANT"));
        mvc.perform(post("/api/v1/me/briefings").with(user(OWNER))).andExpect(status().isForbidden());
    }

    @Test void ownerSnapshotPersistsAndOtherSubscriberCannotReadIt() throws Exception {
        service.setAccess(ownerId,true,"admin");
        var report = service.create(OWNER);
        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.content().items()).isNotEmpty();
        profiles.update(OWNER, UserProfileService.emptyProfile());
        assertThat(service.get(OWNER,report.id())).isEqualTo(report);
        assertThat(jdbc.queryForObject("SELECT profile_snapshot->>'regionCode' FROM personal_briefing WHERE id=?",String.class,report.id())).isEqualTo("SEOUL");
        mvc.perform(get("/api/v1/me/briefings/"+report.id()).with(user(OWNER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        var pdf = mvc.perform(get("/api/v1/me/briefings/"+report.id()+"/pdf").with(user(OWNER)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document=org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document)).contains("맞춤 정책 브리핑",report.content().items().getFirst().name());
        }
        service.setAccess(users.findByEmailIgnoreCase(OTHER).orElseThrow().getId(),true,"admin");
        mvc.perform(get("/api/v1/me/briefings/"+report.id()).with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/briefings/"+report.id()+"/pdf").with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/briefings/"+UUID.randomUUID()).with(user(OTHER))).andExpect(status().isNotFound());
        assertThat(service.list(OTHER)).isEmpty();
    }

    @Test void revocationImmediatelyBlocksOldReportsAndCreation() throws Exception {
        service.setAccess(ownerId,true,"admin");
        var report=service.create(OWNER);
        service.setAccess(ownerId,false,"admin");
        mvc.perform(get("/api/v1/me/briefings/"+report.id()).with(user(OWNER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/briefings/"+report.id()+"/pdf").with(user(OWNER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/briefings").with(user(OWNER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/briefings").with(user(OWNER)).with(csrf())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT updated_by FROM briefing_test_access WHERE user_id=?",String.class,ownerId)).isEqualTo("admin");
    }

    @Test void missingAndEmptySavedProfilesAreExplicitConflicts() throws Exception {
        service.setAccess(ownerId,true,"admin");
        entityManager.flush();
        jdbc.update("DELETE FROM user_profile WHERE user_id=?",ownerId);
        entityManager.clear();
        mvc.perform(post("/api/v1/me/briefings").with(user(OWNER)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("프로필을 저장한 뒤 브리핑을 생성해 주세요."));
        profiles.update(OWNER,UserProfileService.emptyProfile());
        mvc.perform(post("/api/v1/me/briefings").with(user(OWNER)).with(csrf())).andExpect(status().isConflict());
        assertThat(service.list(OWNER)).isEmpty();
    }

    @Test void failureIsRecordedAndNextAttemptCanSucceed() {
        service.setAccess(ownerId,true,"admin");
        doThrow(new IllegalStateException("private failure detail")).doCallRealMethod().when(generator).generate(any(),any());
        var failed=service.create(OWNER);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.content()).isNull();
        assertThat(failed.failureMessage()).doesNotContain("private");
        assertThat(service.get(OWNER,failed.id())).isEqualTo(failed);
        assertThat(service.create(OWNER).status()).isEqualTo("COMPLETED");
        assertThat(service.list(OWNER)).hasSize(2);
    }
}
