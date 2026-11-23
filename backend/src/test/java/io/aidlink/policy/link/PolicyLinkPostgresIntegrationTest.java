package io.aidlink.policy.link;

import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"aidlink.redis.enabled=false", "aidlink.links.scheduling-enabled=false", "aidlink.collector.gov24.scheduling-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "AIDLINK_LINK_DB_TEST", matches = "true")
class PolicyLinkPostgresIntegrationTest {
    @Autowired PolicyLinkService service;
    @Autowired PolicyJpaRepository policies;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean PolicyLinkInspector inspector;
    private long id;
    private static final String URL = "https://provider.or.kr/policy/123";

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM policy WHERE external_id = 'LINK-CHECK-TEST'");
        id = policies.saveAndFlush(new PolicyEntity("LINK-CHECK-TEST", "청년 월세 지원", "시험 제공기관", "주거", "SEOUL",
                "설명", "청년", "월세 지원", null, null, "방문 신청", "신청서", "https://apply.or.kr/start",
                "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcId=TEST", PolicyAnalysisStatus.RAW, null)).getId();
        when(inspector.inspect(URL, "청년 월세 지원")).thenReturn(new LinkCheckResult(LinkCheckResult.Status.CONTENT_MATCH, URL, 200, "본문 후보"));
    }

    @Test
    void reviewRequiresAdminCsrfFreshMatchAndEvidenceThenSurvivesCollection() throws Exception {
        String endpoint = "/api/v1/admin/policies/links/" + id;
        mvc.perform(get("/api/v1/admin/policies/links")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/policies/links").with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(endpoint + "/check").with(user("reviewer").roles("ADMIN")).contentType("application/json").content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint + "/check").with(user("reviewer").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTENT_MATCH")).andExpect(jsonPath("$.verifiedSourceUrl").isEmpty());
        mvc.perform(put(endpoint + "/approve").with(user("reviewer").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content("{\"url\":\"" + URL + "\",\"evidence\":\"제공기관과 정책명, 청년 지원 대상 및 방문 신청 절차의 본문 일치를 확인했습니다.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verifiedSourceUrl").value(URL)).andExpect(jsonPath("$.reviewedBy").value("reviewer"));
        PolicyEntity collected = policies.findById(id).orElseThrow();
        collected.update("청년 월세 지원", "시험 제공기관", "주거", "SEOUL", "새 설명", "청년", "지원", null, null,
                "방문", "신청서", "https://apply.or.kr/start", "https://www.gov.kr/changed-api-source", PolicyAnalysisStatus.RAW, null);
        policies.saveAndFlush(collected);
        assertThat(policies.findById(id).orElseThrow().getSourceUrl()).isEqualTo(URL);
        assertThat(service.get(id).sourceUrl()).endsWith("changed-api-source");
        jdbc.update("UPDATE policy SET source_link_checked_at = now() - interval '2 days' WHERE id = ?", id);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approve(id, URL, "제공기관과 정책명 및 지원 대상과 신청 절차를 직접 확인했습니다.", "reviewer"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void failuresHideBrokenMappingPreserveItForRetryAndRecoverAfterMatch() {
        service.check(id, URL);
        service.approve(id, URL, "제공기관과 정책명 및 지원 대상과 신청 절차를 직접 확인했습니다.", "reviewer");
        when(inspector.inspect(URL, "청년 월세 지원")).thenReturn(new LinkCheckResult(LinkCheckResult.Status.MAINTENANCE, URL, 503, "점검"));
        var failure = service.check(id, null);
        assertThat(failure.failures()).isEqualTo(1);
        assertThat(failure.verifiedSourceUrl()).isEqualTo(URL);
        assertThat(failure.nextCheckAt()).isAfter(failure.checkedAt());
        assertThat(policies.findById(id).orElseThrow().getSourceUrl()).isEmpty();
        String otherCandidate = "https://provider.or.kr/other-candidate";
        when(inspector.inspect(otherCandidate, "청년 월세 지원")).thenReturn(new LinkCheckResult(LinkCheckResult.Status.OTHER_PAGE, otherCandidate, 200, "다른 본문"));
        service.check(id, otherCandidate);
        assertThat(policies.findById(id).orElseThrow().getSourceUrl()).isEmpty();
        when(inspector.inspect(URL, "청년 월세 지원")).thenReturn(new LinkCheckResult(LinkCheckResult.Status.CONTENT_MATCH, URL, 200, "복구"));
        assertThat(service.check(id, null).failures()).isZero();
        assertThat(policies.findById(id).orElseThrow().getSourceUrl()).isEqualTo(URL);
    }

    @Test
    void boundedDueChecksAndPageClampingUseDatabaseState() {
        jdbc.update("UPDATE policy SET source_link_next_check_at = now() + interval '7 days'");
        jdbc.update("UPDATE policy SET source_link_next_check_at = NULL, verified_source_url = ? WHERE id = ?", URL, id);
        assertThat(service.checkDue(1)).containsEntry("checked", 1);
        assertThat(service.checkDue(1)).containsEntry("checked", 0);
        var page = service.list(999, "CONTENT_MATCH", "청년 월세 지원");
        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isZero();
        assertThat(service.list(3, "CONNECTION_FAILED", "없는정책").content()).isEmpty();
    }
}
