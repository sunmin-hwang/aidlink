package io.aidlink.user.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 변경할 값만 채우는 시뮬레이션 전용 프로필 패치입니다. */
public record ProfileChanges(
        LocalDate birthDate,
        String regionCode,
        EmploymentStatus employmentStatus,
        StudentStatus studentStatus,
        BigDecimal personalMonthlyIncome,
        BigDecimal householdMonthlyIncome,
        BigDecimal medianIncomeRatio,
        Integer weeklyWorkHours,
        Integer householdSize,
        String householdType,
        String housingType,
        HomeOwnership homeOwnership,
        BigDecimal asset,
        MaritalStatus maritalStatus,
        Integer childrenCount,
        BusinessStatus businessStatus,
        Integer businessAgeMonths,
        BigDecimal businessMonthlyRevenue
) {
    public UserProfile applyTo(UserProfile base) {
        return new UserProfile(
                valueOr(birthDate, base.birthDate()),
                valueOr(regionCode, base.regionCode()),
                valueOr(employmentStatus, base.employmentStatus()),
                valueOr(studentStatus, base.studentStatus()),
                valueOr(personalMonthlyIncome, base.personalMonthlyIncome()),
                valueOr(householdMonthlyIncome, base.householdMonthlyIncome()),
                valueOr(medianIncomeRatio, base.medianIncomeRatio()),
                valueOr(weeklyWorkHours, base.weeklyWorkHours()),
                valueOr(householdSize, base.householdSize()),
                valueOr(householdType, base.householdType()),
                valueOr(housingType, base.housingType()),
                valueOr(homeOwnership, base.homeOwnership()),
                valueOr(asset, base.asset()),
                valueOr(maritalStatus, base.maritalStatus()),
                valueOr(childrenCount, base.childrenCount()),
                valueOr(businessStatus, base.businessStatus()),
                valueOr(businessAgeMonths, base.businessAgeMonths()),
                valueOr(businessMonthlyRevenue, base.businessMonthlyRevenue())
        );
    }

    private static <T> T valueOr(T changed, T original) {
        return changed != null ? changed : original;
    }
}

