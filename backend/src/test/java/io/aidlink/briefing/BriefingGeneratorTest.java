package io.aidlink.briefing;

import io.aidlink.briefing.application.*;
import io.aidlink.eligibility.application.*;
import io.aidlink.eligibility.domain.*;
import io.aidlink.observability.AidLinkMetrics;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.domain.*;
import io.aidlink.user.application.UserProfileService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BriefingGeneratorTest {
    @Test void draftsWithConcreteChecksSurviveTheLimitWithoutBeingPromoted() {
        var catalog=mock(EligibilityPolicyCatalog.class);
        var engine=new EligibilityService(new RuleEngine(new ConditionValueResolver()),new NearMissClassifier(),mock(AidLinkMetrics.class));
        var manual=new ConditionRule(ConditionField.MANUAL_REVIEW,Operator.REQUIRED,List.of(),"교육 방식과 단체 인원 확인");
        var known=new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("SEOUL"),"서울 거주");
        when(catalog.findWithRules(any(),any())).thenReturn(List.of(
                policy(101,PolicyAnalysisStatus.REVIEW_REQUIRED,manual,null,""),
                policy(102,PolicyAnalysisStatus.AUTO_VALIDATED,manual,null,""),
                policy(103,PolicyAnalysisStatus.HUMAN_VERIFIED,manual,null,""),
                policy(104,PolicyAnalysisStatus.AUTO_VALIDATED,known,null,""),
                policy(105,PolicyAnalysisStatus.RAW,manual,null,"")));
        var raw=java.util.stream.LongStream.rangeClosed(1,25)
                .mapToObj(id->policy(id,PolicyAnalysisStatus.RAW,null,LocalDate.of(2026,11,28),"")).toList();
        when(catalog.findPage(any(),any(),anyList(),any(),anyInt(),anyInt(),any()))
                .thenReturn(new PageResult<>(raw,25,0,100));
        var profile=new io.aidlink.user.domain.UserProfile(null,"SEOUL",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        var result=new BriefingGenerator(catalog,engine).generate(profile,Instant.parse("2026-11-27T00:00:00Z"));
        assertThat(result.items()).hasSize(20);
        assertThat(result.items().subList(0,4)).extracting(BriefingContent.Item::policyId)
                .containsExactly(104L,103L,102L,101L);
        var draft=result.items().get(3);
        assertThat(draft.status()).isEqualTo(EligibilityStatus.UNKNOWN);
        assertThat(draft.analysisStatus()).isEqualTo(PolicyAnalysisStatus.REVIEW_REQUIRED);
        assertThat(draft.checkConditions()).anyMatch(s->s.contains("교육 방식과 단체 인원 확인"));
        assertThat(result.items()).extracting(BriefingContent.Item::policyId).doesNotContain(105L);
        assertThat(result.evaluatedCount()).isEqualTo(30);
    }

    @Test void conservativeStatusDeadlineFilteringOrderingAndSafeLinks() {
        var catalog=mock(EligibilityPolicyCatalog.class);
        var engine = new EligibilityService(new RuleEngine(new ConditionValueResolver()),new NearMissClassifier(),mock(AidLinkMetrics.class));
        var rule=new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("SEOUL"),"서울 거주");
        var raw=policy(1,PolicyAnalysisStatus.RAW,rule,LocalDate.of(2026,12,31),"javascript:alert(1)");
        var reviewed=policy(2,PolicyAnalysisStatus.HUMAN_VERIFIED,
                new OrRule(List.of(rule, new ConditionRule(ConditionField.AGE,Operator.GTE,List.of("19"),"만 19세 이상"))),
                LocalDate.of(2026,12,31),"https://agency.go.kr/policy");
        var expired=policy(3,PolicyAnalysisStatus.HUMAN_VERIFIED,rule,LocalDate.of(2026,11,25),"https://agency.go.kr/policy");
        when(catalog.findWithRules(any(),any())).thenReturn(List.of(raw,reviewed,expired));
        when(catalog.findPage(any(),any(),anyList(),any(),anyInt(),anyInt(),any())).thenReturn(new PageResult<>(List.of(),0,0,100));
        var profile=new io.aidlink.user.domain.UserProfile(null,"SEOUL",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        var content=new BriefingGenerator(catalog,engine).generate(profile,Instant.parse("2026-11-25T16:00:00Z"));
        assertThat(content.evaluationDate()).isEqualTo(LocalDate.of(2026,11,26));
        assertThat(content.items()).extracting(BriefingContent.Item::policyId).containsExactly(2L,1L);
        assertThat(content.items().getFirst().status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(content.items().getFirst().checkConditions()).isEmpty();
        assertThat(content.items().getLast().status()).isEqualTo(EligibilityStatus.UNKNOWN);
        assertThat(content.items().getLast().sourceUrl()).isEmpty();
        assertThat(content.items().getLast().checkConditions()).isNotEmpty();
    }

    @Test void noCandidatesProducesSuccessfulEmptyContentAndMissingValuesStayUnknown() {
        var catalog=mock(EligibilityPolicyCatalog.class);
        var engine=new EligibilityService(new RuleEngine(new ConditionValueResolver()),new NearMissClassifier(),mock(AidLinkMetrics.class));
        when(catalog.findWithRules(any(),any())).thenReturn(List.of());
        when(catalog.findPage(any(),any(),anyList(),any(),anyInt(),anyInt(),any())).thenReturn(new PageResult<>(List.of(),0,0,100));
        var generator=new BriefingGenerator(catalog,engine);
        assertThat(generator.generate(UserProfileService.emptyProfile(),Instant.now()).items()).isEmpty();
        var policy=policy(1,PolicyAnalysisStatus.HUMAN_VERIFIED,new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("SEOUL"),"서울 거주"),null,"");
        when(catalog.findWithRules(any(),any())).thenReturn(List.of(policy));
        assertThat(generator.generate(UserProfileService.emptyProfile(),Instant.now()).items().getFirst().status()).isEqualTo(EligibilityStatus.UNKNOWN);
    }

    private Policy policy(long id,PolicyAnalysisStatus status,RuleNode rule,LocalDate end,String source) {
        return new Policy(id,"BRIEFING-TEST:"+id,"정책"+id,"기관","주거","SEOUL","설명","대상","지원",null,end,"온라인 신청","서류","https://agency.go.kr/apply",source,null,status,rule);
    }

    @Test void unknownResultOnlyExplainsTheRelevantOrBranch() {
        var catalog=mock(EligibilityPolicyCatalog.class);
        var engine=new EligibilityService(new RuleEngine(new ConditionValueResolver()),new NearMissClassifier(),mock(AidLinkMetrics.class));
        var rule=new AndRule(List.of(
                new ConditionRule(ConditionField.PERSONAL_INCOME,Operator.LTE,List.of("3000000"),"개인소득 확인"),
                new OrRule(List.of(new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("SEOUL"),"서울 거주"),
                        new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("GYEONGGI"),"사용하지 않은 경기 조건")))));
        when(catalog.findWithRules(any(),any())).thenReturn(List.of(policy(1,PolicyAnalysisStatus.HUMAN_VERIFIED,rule,null,"")));
        when(catalog.findPage(any(),any(),anyList(),any(),anyInt(),anyInt(),any())).thenReturn(new PageResult<>(List.of(),0,0,100));
        var profile=new io.aidlink.user.domain.UserProfile(null,"SEOUL",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        var item=new BriefingGenerator(catalog,engine).generate(profile,Instant.now()).items().getFirst();
        assertThat(item.status()).isEqualTo(EligibilityStatus.UNKNOWN);
        assertThat(item.checkConditions()).anyMatch(s->s.contains("개인소득 확인")).noneMatch(s->s.contains("사용하지 않은 경기 조건"));
    }

    @Test void limitsReportSizeAndDisclosesUnstructuredCandidateLimit() {
        var catalog=mock(EligibilityPolicyCatalog.class);
        var engine=new EligibilityService(new RuleEngine(new ConditionValueResolver()),new NearMissClassifier(),mock(AidLinkMetrics.class));
        var rule=new ConditionRule(ConditionField.REGION,Operator.EQ,List.of("SEOUL"),"서울 거주");
        var candidates=java.util.stream.LongStream.rangeClosed(1,25).mapToObj(id->policy(id,PolicyAnalysisStatus.HUMAN_VERIFIED,rule,null,"")).toList();
        when(catalog.findWithRules(any(),any())).thenReturn(candidates);
        when(catalog.findPage(any(),any(),anyList(),any(),anyInt(),anyInt(),any()))
                .thenReturn(new PageResult<>(List.of(policy(1000,PolicyAnalysisStatus.RAW,null,null,"")),500,0,100));
        var content=new BriefingGenerator(catalog,engine).generate(UserProfileService.emptyProfile(),Instant.now());
        assertThat(content.items()).hasSize(20);
        assertThat(content.evaluatedCount()).isEqualTo(26);
        assertThat(content.candidateLimitReached()).isTrue();
    }
}
