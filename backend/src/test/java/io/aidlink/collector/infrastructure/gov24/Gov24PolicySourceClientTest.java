package io.aidlink.collector.infrastructure.gov24;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.aidlink.observability.AidLinkMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Gov24PolicySourceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Gov24PolicySourceClient client = new Gov24PolicySourceClient(
            RestClient.create(), new Gov24ApiProperties(),
            new AidLinkMetrics(new SimpleMeterRegistry()));

    @Test
    void parsesTheOfficialKoreanFieldNames() throws Exception {
        var response = objectMapper.readTree("""
                {
                  "page": 1,
                  "perPage": 2,
                  "totalCount": 10,
                  "currentCount": 2,
                  "data": [
                    {"서비스ID":"SVC-1","서비스명":"청년 월세 지원","지원대상":"청년"},
                    {"SVC_ID":"SVC-2","서비스명":"취업 지원","지원내용":"교육"}
                  ]
                }
                """);

        var page = client.parsePage(response, 1, 2);

        assertThat(page.totalCount()).isEqualTo(10);
        assertThat(page.policies()).extracting(policy -> policy.externalId())
                .containsExactly("SVC-1", "SVC-2");
    }

    @Test
    void failsClosedWhenRequiredFieldsDisappear() throws Exception {
        var response = objectMapper.readTree("""
                {"page":1,"perPage":1,"totalCount":1,"data":[{"서비스명":"ID 없는 정책"}]}
                """);

        assertThatThrownBy(() -> client.parsePage(response, 1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing its ID");
    }

    @Test
    void mergesOnlineApplicationDataFromTheDetailDatasetByServiceId() throws Exception {
        var list = client.parsePage(objectMapper.readTree("""
                {"page":1,"perPage":2,"totalCount":2,"data":[
                  {"서비스ID":"SVC-1","서비스명":"첫 정책","서비스분야":"생활","상세조회URL":"https://www.gov.kr/detail/1"},
                  {"서비스ID":"SVC-2","서비스명":"둘째 정책","서비스분야":"주거","상세조회URL":"https://www.gov.kr/detail/2"}
                ]}
                """), 1, 2);
        var detail = client.parsePage(objectMapper.readTree("""
                {"page":1,"perPage":2,"totalCount":2,"data":[
                  {"서비스ID":"SVC-2","서비스명":"둘째 정책","온라인신청사이트URL":"https://apply.example/2","구비서류":"서류 2"},
                  {"서비스ID":"SVC-1","서비스명":"첫 정책","온라인신청사이트URL":"https://apply.example/1","구비서류":"서류 1"}
                ]}
                """), 1, 2);

        var merged = client.mergePages(list, detail);

        assertThat(merged.policies()).extracting(policy -> policy.externalId())
                .containsExactly("SVC-1", "SVC-2");
        assertThat(merged.policies().getFirst().payload().path("서비스분야").asText())
                .isEqualTo("생활");
        assertThat(merged.policies().getFirst().payload().path("온라인신청사이트URL").asText())
                .isEqualTo("https://apply.example/1");
        assertThat(merged.policies().getFirst().payload().path("구비서류").asText())
                .isEqualTo("서류 1");
    }
}
