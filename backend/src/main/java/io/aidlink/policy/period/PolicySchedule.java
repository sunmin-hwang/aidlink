package io.aidlink.policy.period;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Embeddable
public class PolicySchedule {
    @Column(name="application_period_raw", nullable=false, columnDefinition="text") private String raw = "";
    @Enumerated(EnumType.STRING) @Column(name="application_period_kind", nullable=false) private ApplicationWindow.Kind kind = ApplicationWindow.Kind.UNKNOWN;
    @Column(name="source_application_start") private LocalDate sourceStart;
    @Column(name="source_application_end") private LocalDate sourceEnd;
    @Column(name="source_application_start_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime sourceStartTime;
    @Column(name="source_application_end_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime sourceEndTime;
    @Column(name="application_start_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime startTime;
    @Column(name="application_end_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime endTime;
    @Column(name="verified_application_period_text", columnDefinition="text") private String verifiedText;
    @Column(name="verified_application_start") private LocalDate verifiedStart;
    @Column(name="verified_application_end") private LocalDate verifiedEnd;
    @Column(name="verified_application_start_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime verifiedStartTime;
    @Column(name="verified_application_end_time") @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType.class) private LocalTime verifiedEndTime;
    @Column(name="application_period_verified_url", columnDefinition="text") private String verifiedUrl;
    @Column(name="application_period_verified_at") private Instant verifiedAt;
    @Column(name="application_period_reviewed_by") private String reviewer;
    @Column(name="application_period_review_evidence", columnDefinition="text") private String evidence;
    @Column(name="application_period_review_required", nullable=false) private boolean reviewRequired;

    public ApplicationWindow source(String method, LocalDate start, LocalDate end) {
        ApplicationWindow parsed = ApplicationPeriodParser.fromSource(method, start, end);
        String nextRaw = ApplicationPeriodParser.sourceText(method);
        if (verifiedAt != null && !raw.equals(nextRaw)) {
            reviewRequired = !parsed.equals(new ApplicationWindow(ApplicationWindow.Kind.FIXED, verifiedStart, verifiedEnd, verifiedStartTime, verifiedEndTime));
        }
        raw = nextRaw;
        sourceStart = parsed.start(); sourceEnd = parsed.end();
        sourceStartTime = parsed.startTime(); sourceEndTime = parsed.endTime();
        ApplicationWindow effective = verifiedAt == null ? parsed
                : new ApplicationWindow(ApplicationWindow.Kind.FIXED, verifiedStart, verifiedEnd, verifiedStartTime, verifiedEndTime);
        kind = effective.kind(); startTime = effective.startTime(); endTime = effective.endTime();
        return effective;
    }

    public ApplicationWindow verify(String text, String url, String reason, String actor, Instant now) {
        ApplicationWindow value = ApplicationPeriodParser.parse(text);
        if (value.kind() != ApplicationWindow.Kind.FIXED) throw new IllegalArgumentException("연도를 포함한 확정 신청기간을 입력해 주세요.");
        verifiedText = text; verifiedStart = value.start(); verifiedEnd = value.end();
        verifiedStartTime = value.startTime(); verifiedEndTime = value.endTime();
        verifiedUrl = url; verifiedAt = now; reviewer = actor; evidence = reason;
        reviewRequired = false;
        kind = value.kind(); startTime = value.startTime(); endTime = value.endTime();
        return value;
    }

    public ApplicationWindow window(LocalDate start, LocalDate end) { return new ApplicationWindow(kind, start, end, startTime, endTime); }
    public PublicApplicationPeriod publicView() { return new PublicApplicationPeriod(kind, raw, startTime, endTime, verifiedAt == null ? "API" : "PROVIDER_REVIEW", verifiedUrl, verifiedAt, reviewRequired); }
    public String raw() { return raw; }
    public String verifiedText() { return verifiedText; }
    public String reviewer() { return reviewer; }
    public String evidence() { return evidence; }
}
