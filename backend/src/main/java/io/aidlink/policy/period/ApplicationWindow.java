package io.aidlink.policy.period;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ApplicationWindow(Kind kind, LocalDate start, LocalDate end, LocalTime startTime, LocalTime endTime) {
    public enum Kind { UNKNOWN, YEAR_ROUND, FIXED }

    public static ApplicationWindow unknown() { return new ApplicationWindow(Kind.UNKNOWN, null, null, null, null); }

    public ApplicationWindow {
        if ((startTime != null && start == null) || (endTime != null && end == null)
                || (start != null && end != null && start.isAfter(end))
                || (start != null && start.equals(end) && startTime != null && endTime != null && startTime.isAfter(endTime))) {
            throw new IllegalArgumentException("신청기간의 시작·종료 순서를 확인해 주세요.");
        }
        if (kind == Kind.FIXED && start == null && end == null) throw new IllegalArgumentException("확정된 날짜가 필요합니다.");
    }

    public boolean expired(LocalDateTime now) {
        return end != null && (end.isBefore(now.toLocalDate())
                || (end.equals(now.toLocalDate()) && endTime != null && now.toLocalTime().isAfter(endTime)));
    }

    public boolean openAt(LocalDateTime now) {
        if (kind == Kind.UNKNOWN || expired(now)) return false;
        return start == null || start.isBefore(now.toLocalDate())
                || (start.equals(now.toLocalDate()) && (startTime == null || !now.toLocalTime().isBefore(startTime)));
    }
}
