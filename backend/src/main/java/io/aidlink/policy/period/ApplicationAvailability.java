package io.aidlink.policy.period;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

/** Availability hints do not turn recurring source prose into verified calendar dates. */
public final class ApplicationAvailability {
    private ApplicationAvailability() { }
    private static final Set<String> NO_APPLICATION = Set.of("별도의신청절차가없음", "별도신청절차없음",
            "개인신청절차없음", "신청불필요", "신청불요");
    private static final Pattern MONTH = Pattern.compile("^매년(?:도)?([0-9]{1,2})월(?:중|신청)?$");
    private static final Pattern MONTHS = Pattern.compile("^매년(?:도)?([0-9]{1,2})월?[~∼～-]([0-9]{1,2})월(?:중|신청)?$");

    public static boolean openAt(ApplicationWindow window, String raw, boolean reviewRequired, LocalDateTime now) {
        if (reviewRequired) return false;
        return window.kind() == ApplicationWindow.Kind.UNKNOWN
                ? guidanceOpen(raw, now.toLocalDate()) : window.openAt(now);
    }

    public static boolean guidanceOpen(String raw, LocalDate today) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (NO_APPLICATION.contains(value)) return true;
        var single = MONTH.matcher(value);
        var range = MONTHS.matcher(value);
        int start;
        int end;
        if (single.matches()) {
            start = end = Integer.parseInt(single.group(1));
        } else if (range.matches()) {
            start = Integer.parseInt(range.group(1));
            end = Integer.parseInt(range.group(2));
        } else return false;
        if (start < 1 || start > 12 || end < 1 || end > 12) return false;
        int month = today.getMonthValue();
        return start <= end ? month >= start && month <= end : month >= start || month <= end;
    }
}
