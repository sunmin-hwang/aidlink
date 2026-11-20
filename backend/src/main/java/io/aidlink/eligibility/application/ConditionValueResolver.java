package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.user.domain.UserProfile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

@Component
public class ConditionValueResolver {

    public Object resolve(ConditionField field, UserProfile profile, LocalDate evaluationDate) {
        return switch (field) {
            case AGE -> profile.birthDate() == null
                    ? null
                    : Period.between(profile.birthDate(), evaluationDate).getYears();
            case REGION -> profile.regionCode();
            case PERSONAL_INCOME -> profile.personalMonthlyIncome();
            case HOUSEHOLD_INCOME -> profile.householdMonthlyIncome();
            case MEDIAN_INCOME_RATIO -> profile.medianIncomeRatio();
            case EMPLOYMENT -> profile.employmentStatus();
            case WEEKLY_WORK_HOURS -> profile.weeklyWorkHours();
            case STUDENT_STATUS -> profile.studentStatus();
            case HOUSEHOLD_SIZE -> profile.householdSize();
            case HOUSEHOLD_TYPE -> profile.householdType();
            case HOUSING_TYPE -> profile.housingType();
            case HOME_OWNERSHIP -> profile.homeOwnership();
            case ASSET -> profile.asset();
            case MARITAL_STATUS -> profile.maritalStatus();
            case CHILDREN_COUNT -> profile.childrenCount();
            case BUSINESS_STATUS -> profile.businessStatus();
            case BUSINESS_AGE -> profile.businessAgeMonths();
            case BUSINESS_REVENUE -> profile.businessMonthlyRevenue();
            case MANUAL_REVIEW -> null;
        };
    }
}

