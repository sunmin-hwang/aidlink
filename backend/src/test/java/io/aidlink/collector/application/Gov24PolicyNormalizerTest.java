package io.aidlink.collector.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24PolicyNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Gov24PolicyNormalizer normalizer = new Gov24PolicyNormalizer();

    @Test
    void mapsOfficialFieldsWithoutInventingAnApplicationPeriod() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "서비스ID":"SVC-SEOUL-1",
                  "서비스명":"서울 청년 지원",
                  "서비스목적요약":"청년의 생활 안정을 지원합니다.",
                  "서비스분야":"생활안정",
                  "지원대상":"만 19세 이상",
                  "선정기준":"서울 거주자",
                  "지원내용":"월 10만원",
                  "신청기한":"상시",
                  "신청방법":"온라인",
                  "구비서류":"신분증",
                  "소관기관명":"서울특별시 청년정책과",
                  "온라인신청사이트URL":"https://example.go.kr/apply",
                  "상세조회URL":"https://example.go.kr/detail",
                  "수정일시":"2026-10-08 09:30:00"
                }
                """);

        var policy = normalizer.normalize(
                new RawPolicyDocument(PolicySource.GOV24, "SVC-SEOUL-1", payload));

        assertThat(policy.externalId()).isEqualTo("GOV24:SVC-SEOUL-1");
        assertThat(policy.regionCode()).isEqualTo("SEOUL");
        assertThat(policy.targetDescription()).contains("지원대상", "선정기준");
        assertThat(policy.applicationMethod()).contains("상시");
        assertThat(policy.requiredDocuments()).isEqualTo("신분증");
        assertThat(policy.applicationUrl()).isEqualTo("https://example.go.kr/apply");
        assertThat(policy.sourceUrl()).isEqualTo("https://example.go.kr/detail");
        assertThat(policy.applicationStart()).isNull();
        assertThat(policy.applicationEnd()).isNull();
        assertThat(policy.analysisStatus()).isEqualTo(PolicyAnalysisStatus.RAW);
        assertThat(policy.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-08T00:30:00Z"));
    }

    @Test
    void doesNotInventADetailUrlWhenApiLinksAreUnavailable()
            throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "서비스ID":"116010000001",
                  "서비스명":"주택금융공사 월세자금보증",
                  "소관기관명":"한국주택금융공사",
                  "신청방법":"방문신청"
                }
                """);

        var policy = normalizer.normalize(
                new RawPolicyDocument(PolicySource.GOV24, "116010000001", payload));

        assertThat(policy.sourceUrl())
                .isEmpty();
        assertThat(policy.applicationUrl()).isEqualTo(policy.sourceUrl());
    }

    @Test
    void preservesTheGov24DetailUrlReturnedByTheApi() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "서비스ID":"PG4CADM0681",
                  "서비스명":"공식 링크 전환 테스트",
                  "상세조회URL":"https://www.gov.kr/portal/rcvfvrSvc/dtlEx/PG4CADM0681"
                }
                """);

        var policy = normalizer.normalize(
                new RawPolicyDocument(PolicySource.GOV24, "PG4CADM0681", payload));

        assertThat(policy.sourceUrl())
                .isEqualTo("https://www.gov.kr/portal/rcvfvrSvc/dtlEx/PG4CADM0681");
        assertThat(policy.applicationUrl()).isEqualTo(policy.sourceUrl());
    }

    @Test
    void preservesTheCompletePlusPortalUrlIncludingItsQueryParameters() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "서비스ID":"116010000001",
                  "서비스명":"혜택 상세 링크 전환 테스트",
                  "상세조회URL":"https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=754881&svcId=116010000001"
                }
                """);

        var policy = normalizer.normalize(
                new RawPolicyDocument(PolicySource.GOV24, "116010000001", payload));

        assertThat(policy.sourceUrl())
                .isEqualTo("https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=754881&svcId=116010000001");
        assertThat(policy.applicationUrl()).isEqualTo(policy.sourceUrl());
    }
}
