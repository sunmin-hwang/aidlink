package io.aidlink.policy.period;

import java.time.Instant;
import java.time.LocalTime;

public record PublicApplicationPeriod(ApplicationWindow.Kind kind, String sourceText,
                                      LocalTime startTime, LocalTime endTime,
                                      String origin, String verifiedSourceUrl, Instant verifiedAt, boolean reviewRequired) { }
