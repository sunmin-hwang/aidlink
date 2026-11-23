package io.aidlink.policy.period;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class ApplicationAvailabilityTest {
    @Test void noApplicationGuidanceIsAvailableWithoutTreatingConditionalTextAsUnconditional() {
        for (String text : new String[]{"별도의 신청절차가 없음", "별도 신청절차 없음", "개인 신청절차 없음", "신청 불필요", "신청불요"}) {
            assertThat(ApplicationAvailability.guidanceOpen(text, LocalDate.of(2026,11,23))).isTrue();
        }
        for (String text : new String[]{"기존 대상자는 신청 불필요, 신규는 1월 신청", "접수기관 별 상이", "별도 공고", "", "미정"}) {
            assertThat(ApplicationAvailability.guidanceOpen(text, LocalDate.of(2026,11,23))).isFalse();
        }
    }

    @Test void recurringMonthsMatchOnlyTheirMonthsAcrossYearAndMonthBoundaries() {
        assertThat(open("매년 1월", "2026-01-01")).isTrue();
        assertThat(open("매년 1월", "2026-01-31")).isTrue();
        assertThat(open("매년 1월", "2026-02-01")).isFalse();
        assertThat(open("매년도 1~2월", "2027-02-28")).isTrue();
        assertThat(open("매년 1월~2월 중", "2026-03-01")).isFalse();
        assertThat(open("매년 11~2월", "2026-12-31")).isTrue();
        assertThat(open("매년 11~2월", "2027-01-01")).isTrue();
        assertThat(open("매년 11~2월", "2027-03-01")).isFalse();
        assertThat(open("매년 1월 신청", "2026-01-01")).isTrue();
        for (String text : new String[]{"매년 0월", "매년 13월", "매년 1~13월", "매년 1월 초", "매년 연초", "매년 1월(모집기간 별도)", "2025년 1월", "매년 1.1~1.15"}) {
            assertThat(open(text, "2026-01-01")).as(text).isFalse();
        }
    }

    @Test void confirmedDatesAndReReviewTakePrecedenceOverHints() {
        var now = LocalDateTime.of(2026,1,15,10,0);
        var expired = new ApplicationWindow(ApplicationWindow.Kind.FIXED, null, LocalDate.of(2025,12,31), null,null);
        assertThat(ApplicationAvailability.openAt(expired,"신청불필요",false,now)).isFalse();
        assertThat(ApplicationAvailability.openAt(ApplicationWindow.unknown(),"매년 1월",true,now)).isFalse();
        assertThat(ApplicationAvailability.openAt(new ApplicationWindow(ApplicationWindow.Kind.YEAR_ROUND,null,null,null,null),"상시신청",false,now)).isTrue();
    }

    private boolean open(String text, String date) {
        return ApplicationAvailability.guidanceOpen(text, LocalDate.parse(date));
    }
}
