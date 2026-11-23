package io.aidlink.policy.period;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPeriodParserTest {
    @ParameterizedTest
    @ValueSource(strings={"2026.10.07~2026.10.20", "2026-10-07 ~ 2026-10-20", "2026/10/07~10/20", "2026년 10월 7일(수) ~ 2026년 10월 20일(화)"})
    void parsesOnlyAnExplicitYearAndUnambiguousRange(String text) {
        var result = ApplicationPeriodParser.parse(text);
        assertThat(result.kind()).isEqualTo(ApplicationWindow.Kind.FIXED);
        assertThat(result.start()).isEqualTo(LocalDate.of(2026,10,7));
        assertThat(result.end()).isEqualTo(LocalDate.of(2026,10,20));
    }

    @ParameterizedTest
    @ValueSource(strings={"", "매년 10.7~10.20", "10.7~10.20", "접수기관 별 상이", "별도 공고", "상시신청(예산 소진 시까지)",
            "2026.02.30~2026.03.01", "2026.10.20~2026.10.07", "2026.10.07 25:00~2026.10.20", "신청 2026.10.07~2026.10.20 사용 2026.12.31까지"})
    void ambiguousInvalidOrMixedContentIsUnknown(String text) { assertThat(ApplicationPeriodParser.parse(text).kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN); }

    @Test
    void preservesKnownTimesWithoutInventingClosingTime() {
        var result = ApplicationPeriodParser.parse("2026년 10월 7일(수) 10:00 ~ 2026년 10월 20일(화)");
        assertThat(result.startTime()).isEqualTo(LocalTime.of(10,0));
        assertThat(result.endTime()).isNull();
        assertThat(result.openAt(LocalDateTime.of(2026,10,7,9,59))).isFalse();
        assertThat(result.openAt(LocalDateTime.of(2026,10,7,10,0))).isTrue();
        assertThat(result.expired(LocalDateTime.of(2026,10,20,23,59))).isFalse();
        assertThat(result.expired(LocalDateTime.of(2026,10,21,0,0))).isTrue();
        assertThat(ApplicationPeriodParser.parse("2026.10.07 10:00~2026.10.07 09:00").kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN);
    }

    @Test
    void deadlineAndStartOnlyAreDistinctAndYearRoundNeedsDedicatedEvidence() {
        assertThat(ApplicationPeriodParser.parse("2026.10.20까지").end()).isEqualTo(LocalDate.of(2026,10,20));
        assertThat(ApplicationPeriodParser.parse("2026.10.07부터").end()).isNull();
        assertThat(ApplicationPeriodParser.parse("연중 상시 신청").kind()).isEqualTo(ApplicationWindow.Kind.YEAR_ROUND);
        assertThat(ApplicationPeriodParser.sourceText("상시 상담 가능\r\n\r\n신청기한: 매년 5월")).isEqualTo("매년 5월");
        assertThat(ApplicationPeriodParser.fromSource("상시 상담 가능", null, null).kind()).isEqualTo(ApplicationWindow.Kind.UNKNOWN);
    }
}
