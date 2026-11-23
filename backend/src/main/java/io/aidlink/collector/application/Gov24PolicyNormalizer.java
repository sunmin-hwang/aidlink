package io.aidlink.collector.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Gov24PolicyNormalizer {

    private static final DateTimeFormatter GOV24_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> REGION_CODES = regionCodes();

    public NormalizedPolicy normalize(RawPolicyDocument document) {
        if (document.source() != PolicySource.GOV24) {
            throw new IllegalArgumentException("Unsupported source: " + document.source());
        }

        JsonNode payload = document.payload();
        String name = required(payload, "서비스명", "serviceName");
        String provider = text(payload, "소관기관명", "접수기관명", "소관기관", "provider");
        String description = text(payload, "서비스목적요약", "서비스목적", "description");
        String target = joinSections(
                section("지원대상", text(payload, "지원대상", "target")),
                section("선정기준", text(payload, "선정기준", "criteria"))
        );
        String supportContent = text(payload, "지원내용", "supportContent");
        String applicationMethod = joinSections(
                section(null, text(payload, "신청방법", "applicationMethod")),
                section("신청기한", text(payload, "신청기한", "deadline"))
        );
        String requiredDocuments = text(payload, "구비서류", "documents");
        var period = io.aidlink.policy.period.ApplicationPeriodParser.parse(text(payload, "신청기한", "deadline"));
        String sourceUrl = firstNonBlank(text(payload, "상세조회URL", "상세URL", "sourceUrl"), "");
        String applicationUrl = firstNonBlank(
                text(payload, "온라인신청사이트URL", "온라인신청URL", "applicationUrl"),
                sourceUrl
        );

        return new NormalizedPolicy(
                limit("GOV24:" + document.externalId(), 160),
                PolicySource.GOV24,
                document.externalId(),
                limit(name, 300),
                limit(defaultText(provider), 200),
                limit(defaultText(text(payload, "서비스분야", "지원분야", "category")), 80),
                regionCode(provider),
                defaultText(description),
                defaultText(target),
                defaultText(supportContent),
                period.start(),
                period.end(),
                defaultText(applicationMethod),
                defaultText(requiredDocuments),
                applicationUrl,
                sourceUrl,
                PolicyAnalysisStatus.RAW,
                parseInstant(text(payload, "수정일시", "수정일", "updatedAt"))
        );
    }

    private String required(JsonNode payload, String... fields) {
        String value = text(payload, fields);
        if (value == null) {
            throw new IllegalStateException("Required Gov24 field is missing: " + String.join("/", fields));
        }
        return value;
    }

    private String text(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode value = payload.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String section(String label, String value) {
        if (value == null) {
            return null;
        }
        return label == null ? value : label + ": " + value;
    }

    private String joinSections(String... sections) {
        return java.util.Arrays.stream(sections)
                .filter(section -> section != null && !section.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value;
    }

    private String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private String regionCode(String provider) {
        if (provider == null) {
            return "NATIONWIDE";
        }
        return REGION_CODES.entrySet().stream()
                .filter(entry -> provider.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("NATIONWIDE");
    }

    private Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        for (InstantParser parser : List.<InstantParser>of(
                text -> Instant.parse(text),
                text -> OffsetDateTime.parse(text).toInstant(),
                text -> LocalDateTime.parse(text, GOV24_DATE_TIME)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant()
        )) {
            try {
                return parser.parse(value);
            } catch (DateTimeParseException ignored) {
                // Try the next official timestamp representation.
            }
        }
        return null;
    }

    private static Map<String, String> regionCodes() {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put("서울", "SEOUL");
        codes.put("부산", "BUSAN");
        codes.put("대구", "DAEGU");
        codes.put("인천", "INCHEON");
        codes.put("광주", "GWANGJU");
        codes.put("대전", "DAEJEON");
        codes.put("울산", "ULSAN");
        codes.put("세종", "SEJONG");
        codes.put("경기", "GYEONGGI");
        codes.put("강원", "GANGWON");
        codes.put("충북", "CHUNGBUK");
        codes.put("충청북도", "CHUNGBUK");
        codes.put("충남", "CHUNGNAM");
        codes.put("충청남도", "CHUNGNAM");
        codes.put("전북", "JEONBUK");
        codes.put("전라북도", "JEONBUK");
        codes.put("전남", "JEONNAM");
        codes.put("전라남도", "JEONNAM");
        codes.put("경북", "GYEONGBUK");
        codes.put("경상북도", "GYEONGBUK");
        codes.put("경남", "GYEONGNAM");
        codes.put("경상남도", "GYEONGNAM");
        codes.put("제주", "JEJU");
        return Map.copyOf(codes);
    }

    @FunctionalInterface
    private interface InstantParser {
        Instant parse(String text) throws DateTimeParseException;
    }
}
