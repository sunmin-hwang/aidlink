package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.collector.domain.*;
import io.aidlink.collector.infrastructure.jpa.JpaNormalizedPolicyStore;
import io.aidlink.policy.application.*;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"aidlink.redis.enabled=false", "aidlink.collector.gov24.scheduling-enabled=false",
        "aidlink.links.scheduling-enabled=false", "aidlink.ai.pipeline.scheduling-enabled=false"})
@ActiveProfiles("postgres")
@Transactional
@EnabledIfEnvironmentVariable(named="AIDLINK_SEARCH_DB_TEST", matches="true")
class PolicySearchVectorPostgresTest {
    private static final String PROVIDER="검색벡터검증기관";
    private static final String LEGACY="""
            to_tsvector('simple', coalesce(p.name, '') || ' ' || coalesce(p.description, '') || ' '
                || coalesce(p.target_description, '') || ' ' || coalesce(p.support_content, '') || ' '
                || coalesce(p.provider, ''))
            """;
    @Autowired JdbcTemplate jdbc;
    @Autowired PolicyJpaRepository repository;
    @Autowired PolicyCatalog catalog;
    @Autowired JpaNormalizedPolicyStore collector;
    @Autowired EntityManager em;

    @BeforeEach void fixtures() {
        for(int i=0;i<4;i++) {
            var entity=new PolicyEntity("SEARCH-VECTOR-TEST:"+i,
                    i==0?"청년 주거 지원":"청년 지원 "+i,PROVIDER,"검색검증",
                    i==1?"NATIONWIDE":"SEOUL","청년 주거 지원 교육","청년 지원 대상","생활 지원",
                    LocalDate.of(2020,1,1),LocalDate.of(2099,12,20+i),
                    "신청기한: 상시신청","서류","https://example.org/apply","https://example.org/source",
                    PolicyAnalysisStatus.RAW,Instant.parse("2026-11-01T00:00:00Z").plusSeconds(i));
            repository.saveAndFlush(entity);
        }
    }

    @Test void storedDocumentMatchesOriginalExpressionForBackfillAndEverySourceFieldUpdate() {
        assertDocumentsEqual();
        long id=repository.findByExternalId("SEARCH-VECTOR-TEST:0").orElseThrow().getId();
        for(String field:List.of("name","description","target_description","support_content","provider")) {
            jdbc.update("UPDATE policy SET "+field+"=? WHERE id=?","고유검색검증어",id);
            assertDocumentsEqual();
            assertThat(jdbc.queryForObject("SELECT search_document @@ websearch_to_tsquery('simple','고유검색검증어') FROM policy WHERE id=?",Boolean.class,id)).isTrue();
            jdbc.update("UPDATE policy SET "+field+"=? WHERE id=?","교체완료문구",id);
            assertThat(jdbc.queryForObject("SELECT search_document @@ websearch_to_tsquery('simple','고유검색검증어') FROM policy WHERE id=?",Boolean.class,id)).isFalse();
        }
    }

    @Test void searchOrderingCountFiltersAndPaginationMatchTheLegacyExpression() {
        var annotation=Arrays.stream(PolicyJpaRepository.class.getMethods())
                .filter(m->m.getName().equals("searchBaseIds")).findFirst().orElseThrow().getAnnotation(Query.class);
        var named=new NamedParameterJdbcTemplate(jdbc);
        for(String query:Arrays.asList(null,"청년","지원","주거","청년 지원","\"청년 주거\"","청년 OR 주거","-지원","!!!","없는정책XYZ")) {
            for(var sort:PolicySearchSort.values()) {
                for(boolean filtered:List.of(false,true)) {
                    var params=new MapSqlParameterSource().addValue("query",query,Types.VARCHAR)
                            .addValue("category",filtered?"검색검증":null,Types.VARCHAR)
                            .addValue("regionCode",filtered?"GYEONGGI":null,Types.VARCHAR)
                            .addValue("provider",PROVIDER,Types.VARCHAR).addValue("openOnly",filtered)
                            .addValue("sort",sort.name()).addValue("excludeDisabled",false)
                            .addValue("excludedPolicyIds",List.of(-1L)).addValue("limit",2).addValue("offset",0);
                    var expected=named.queryForList(annotation.value().replace("p.search_document",LEGACY)+" LIMIT :limit OFFSET :offset",params,Long.class);
                    long total=named.queryForObject(annotation.countQuery().replace("p.search_document",LEGACY),params,Long.class);
                    var actual=repository.searchBaseIds(query,filtered?"검색검증":null,filtered?"GYEONGGI":null,
                            PROVIDER,filtered,sort.name(),false,List.of(-1L),PageRequest.of(0,2));
                    assertThat(actual.getContent()).as("query=%s sort=%s filter=%s",query,sort,filtered).isEqualTo(expected);
                    assertThat(actual.getTotalElements()).isEqualTo(total);
                }
            }
        }
        var last=catalog.search(new PolicySearchCriteria("청년",null,null,PROVIDER,false,null,null,null,999,2,PolicySearchSort.RELEVANCE));
        assertThat(last.totalElements()).isEqualTo(4);
        assertThat(last.page()).isEqualTo(1);
        assertThat(last.content()).hasSize(2);
        var empty=catalog.search(new PolicySearchCriteria("없는정책XYZ",null,null,PROVIDER,false,null,null,null,999,2,PolicySearchSort.RELEVANCE));
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.page()).isZero();
    }

    @Test void realCollectorInsertAndUpdateRefreshTheDocumentWithoutApplicationSideWrites() {
        collector.upsert(collected("수집이전검색문구"),CollectionChangeType.CREATED,Instant.now());
        em.flush();
        assertDocumentsEqual();
        collector.upsert(collected("수집갱신검색문구"),CollectionChangeType.UPDATED,Instant.now());
        em.flush();
        assertDocumentsEqual();
        assertThat(jdbc.queryForObject("SELECT search_document @@ websearch_to_tsquery('simple','수집갱신검색문구') FROM policy WHERE external_id='SEARCH-VECTOR-COLLECTOR'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT search_document @@ websearch_to_tsquery('simple','수집이전검색문구') FROM policy WHERE external_id='SEARCH-VECTOR-COLLECTOR'",Boolean.class)).isFalse();
    }

    private NormalizedPolicy collected(String text) {
        return new NormalizedPolicy("SEARCH-VECTOR-COLLECTOR",PolicySource.GOV24,"SEARCH-VECTOR-COLLECTOR",
                "수집 검색검증",PROVIDER,"검색검증","SEOUL",text,"대상","지원",null,null,
                "신청기한: 상시신청","서류","https://example.org/apply","https://example.org/source",PolicyAnalysisStatus.RAW,null);
    }

    private void assertDocumentsEqual() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM policy p WHERE search_document IS DISTINCT FROM "+LEGACY,Long.class)).isZero();
    }
}
