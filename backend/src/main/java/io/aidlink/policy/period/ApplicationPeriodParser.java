package io.aidlink.policy.period;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative, whole-field parser. Does not infer years or scrape unrelated page dates. */
public final class ApplicationPeriodParser {
    private ApplicationPeriodParser() { }
    private static final Set<String> YEAR_ROUND = Set.of("상시", "상시신청", "상시접수", "연중", "연중신청", "연중접수", "연중상시", "연중상시신청", "연중상시접수");
    private static final String DATE = "(?:(\\d{4})[.\\-/년])?(\\d{1,2})[.\\-/월](\\d{1,2})[.일]?";
    private static final String TIME = "(?:\\((?:월|화|수|목|금|토|일)(?:요일)?\\))?(?:(\\d{1,2}):(\\d{2}))?";
    private static final Pattern RANGE = Pattern.compile("^" + DATE + TIME + "(?:~|∼|～|부터|—|–)" + DATE + TIME + "(?:까지)?$");
    private static final Pattern SINGLE = Pattern.compile("^" + DATE + TIME + "(부터|까지)?$");
    private static final Pattern SECTION = Pattern.compile("(?:^|\\n)신청기한:[ \\t]*([\\s\\S]*)$");

    public static String sourceText(String method) {
        if (method == null) return "";
        var match = SECTION.matcher(method.replace("\r\n", "\n").replace('\r', '\n'));
        return match.find() ? match.group(1).trim() : "";
    }

    public static ApplicationWindow parse(String raw) {
        if (raw == null) return ApplicationWindow.unknown();
        String value = raw.replaceAll("\\s+", "");
        if (YEAR_ROUND.contains(value)) return new ApplicationWindow(ApplicationWindow.Kind.YEAR_ROUND, null, null, null, null);
        try {
            var range = RANGE.matcher(value);
            if (range.matches() && range.group(1) != null) {
                int year = Integer.parseInt(range.group(1));
                LocalDate start = date(year, range.group(2), range.group(3));
                LocalDate end = date(range.group(6) == null ? year : Integer.parseInt(range.group(6)), range.group(7), range.group(8));
                return new ApplicationWindow(ApplicationWindow.Kind.FIXED, start, end,
                        time(range.group(4), range.group(5)), time(range.group(9), range.group(10)));
            }
            var single = SINGLE.matcher(value);
            if (single.matches() && single.group(1) != null) {
                LocalDate date = date(Integer.parseInt(single.group(1)), single.group(2), single.group(3));
                LocalTime time = time(single.group(4), single.group(5));
                return "부터".equals(single.group(6))
                        ? new ApplicationWindow(ApplicationWindow.Kind.FIXED, date, null, time, null)
                        : new ApplicationWindow(ApplicationWindow.Kind.FIXED, null, date, null, time);
            }
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            return ApplicationWindow.unknown();
        }
        return ApplicationWindow.unknown();
    }

    public static ApplicationWindow fromSource(String method, LocalDate suppliedStart, LocalDate suppliedEnd) {
        ApplicationWindow parsed = parse(sourceText(method));
        return parsed.kind() != ApplicationWindow.Kind.UNKNOWN || (suppliedStart == null && suppliedEnd == null)
                ? parsed : new ApplicationWindow(ApplicationWindow.Kind.FIXED, suppliedStart, suppliedEnd, null, null);
    }

    private static LocalDate date(int year, String month, String day) {
        if (year < 1900 || year > 2200) throw new IllegalArgumentException("연도 확인 필요");
        return LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day));
    }

    private static LocalTime time(String hour, String minute) {
        return hour == null ? null : LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
    }
}
