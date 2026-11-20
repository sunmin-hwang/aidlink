package io.aidlink.policy.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPolicyLinkTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/O00108600005",
            "https://gov.kr/portal/rcvfvrSvc/dtlEx/ANY-NEW-POLICY",
            "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=754881&svcId=116010000001",
            "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcId=FUTURE",
            "javascript:alert(1)", "not a url", "https://user:password@example.test/source"
    })
    void blocksBrokenPortalRoutesRegardlessOfPolicyId(String url) {
        assertThat(PublicPolicyLink.usableUrl(url)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://gjmc.or.kr/content/94",
            "https://dle.study.daegu.kr/portal/posts/BBS_00029/detail/29769",
            "https://apply.example.test/start?a=1&b=2",
            "https://www.gov.kr/portal/service/serviceInfo/VALID"
    })
    void keepsProviderAndIndependentApplicationLinks(String url) {
        assertThat(PublicPolicyLink.usableUrl(url)).isEqualTo(url);
    }
}
