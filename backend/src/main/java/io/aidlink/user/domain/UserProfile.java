package io.aidlink.user.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 자격 판정에 사용하는 사용자 정보입니다. 미입력 값은 의도적으로 null을 허용하며,
 * Rule Engine은 이를 실패가 아닌 UNKNOWN으로 평가합니다.
 */
public record UserProfile(
        @PastOrPresent LocalDate birthDate,
        String regionCode,
        EmploymentStatus employmentStatus,
        StudentStatus studentStatus,
        @DecimalMin("0") BigDecimal personalMonthlyIncome,
        @DecimalMin("0") BigDecimal householdMonthlyIncome,
        @DecimalMin("0") BigDecimal medianIncomeRatio,
        @Min(0) @Max(168) Integer weeklyWorkHours,
        @Positive Integer householdSize,
        String householdType,
        String housingType,
        HomeOwnership homeOwnership,
        @DecimalMin("0") BigDecimal asset,
        MaritalStatus maritalStatus,
        @PositiveOrZero Integer childrenCount,
        BusinessStatus businessStatus,
        @PositiveOrZero Integer businessAgeMonths,
        @DecimalMin("0") BigDecimal businessMonthlyRevenue
) {
}

