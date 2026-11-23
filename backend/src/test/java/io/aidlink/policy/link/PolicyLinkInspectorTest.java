package io.aidlink.policy.link;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.aidlink.policy.link.LinkCheckResult.Status.*;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyLinkInspectorTest {
    @Test
    void successCodeAloneDoesNotProvePolicyContent() {
        assertThat(classify(200, "<title>혜택 모아보기</title><p>전체 혜택 검색</p>" )).isEqualTo(OTHER_PAGE);
        assertThat(classify(200, "<p>요청하신 페이지를 찾을 수 없습니다</p>" )).isEqualTo(ERROR_PAGE);
        assertThat(classify(200, "<p>서비스 점검 중입니다</p>" )).isEqualTo(MAINTENANCE);
        assertThat(classify(503, "<p>시스템 점검</p>" )).isEqualTo(MAINTENANCE);
    }

    @Test
    void requiresNameAndApplicationOrSupportContentOutsideNavigationAndScripts() {
        assertThat(classify(200, "<h1>청년 월세 지원</h1><p>지원대상: 청년. 신청방법: 방문 접수</p>" )).isEqualTo(CONTENT_MATCH);
        assertThat(classify(200, "<h1>청년 월세 지원</h1>" )).isEqualTo(OTHER_PAGE);
        assertThat(classify(200, "<nav>청년 월세 지원 신청방법</nav><p>다른 정책</p>" )).isEqualTo(OTHER_PAGE);
        assertThat(classify(200, "<script>청년 월세 지원 신청방법</script>" )).isEqualTo(OTHER_PAGE);
        assertThat(classify(404, "<h1>청년 월세 지원</h1><p>신청방법: 방문</p>" )).isEqualTo(ERROR_PAGE);
    }

    @Test
    void blocksRetiredDeepLinksAndInternalAddressesWithoutRequestingThem() throws Exception {
        var inspector = new PolicyLinkInspector();
        assertThat(inspector.inspect("https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcId=X", "청년 월세 지원").status()).isEqualTo(BLOCKED_URL);
        for (String value : new String[]{"http://127.0.0.1", "http://169.254.169.254/latest/meta-data", "http://localhost", "https://example.com", "file:///etc/passwd", "https://www.gov.kr:8080/a", "https://user:pass@www.gov.kr/a"}) {
            assertThat(PolicyLinkInspector.safeDestination(URI.create(value))).as(value).isFalse();
        }
    }

    private LinkCheckResult.Status classify(int code, String html) {
        return PolicyLinkInspector.classify("https://example.go.kr/policy", code, Jsoup.parse(html), "청년 월세 지원").status();
    }
}
