package io.aidlink.user.infrastructure.jpa;

import io.aidlink.user.domain.BusinessStatus;
import io.aidlink.user.domain.EmploymentStatus;
import io.aidlink.user.domain.HomeOwnership;
import io.aidlink.user.domain.MaritalStatus;
import io.aidlink.user.domain.StudentStatus;
import io.aidlink.user.domain.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;

    private LocalDate birthDate;
    private String regionCode;
    @Enumerated(EnumType.STRING) private EmploymentStatus employmentStatus;
    @Enumerated(EnumType.STRING) private StudentStatus studentStatus;
    @Column(name = "personal_monthly_income") private BigDecimal personalMonthlyIncome;
    @Column(name = "household_monthly_income") private BigDecimal householdMonthlyIncome;
    private BigDecimal medianIncomeRatio;
    private Integer weeklyWorkHours;
    private Integer householdSize;
    private String householdType;
    private String housingType;
    @Enumerated(EnumType.STRING) private HomeOwnership homeOwnership;
    private BigDecimal asset;
    @Enumerated(EnumType.STRING) private MaritalStatus maritalStatus;
    private Integer childrenCount;
    @Enumerated(EnumType.STRING) private BusinessStatus businessStatus;
    private Integer businessAgeMonths;
    @Column(name = "business_monthly_revenue") private BigDecimal businessMonthlyRevenue;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserProfileEntity() {
    }

    public UserProfileEntity(AppUserEntity user, UserProfile profile) {
        this.user = user;
        update(profile);
    }

    public void update(UserProfile profile) {
        this.birthDate = profile.birthDate();
        this.regionCode = profile.regionCode();
        this.employmentStatus = profile.employmentStatus();
        this.studentStatus = profile.studentStatus();
        this.personalMonthlyIncome = profile.personalMonthlyIncome();
        this.householdMonthlyIncome = profile.householdMonthlyIncome();
        this.medianIncomeRatio = profile.medianIncomeRatio();
        this.weeklyWorkHours = profile.weeklyWorkHours();
        this.householdSize = profile.householdSize();
        this.householdType = profile.householdType();
        this.housingType = profile.housingType();
        this.homeOwnership = profile.homeOwnership();
        this.asset = profile.asset();
        this.maritalStatus = profile.maritalStatus();
        this.childrenCount = profile.childrenCount();
        this.businessStatus = profile.businessStatus();
        this.businessAgeMonths = profile.businessAgeMonths();
        this.businessMonthlyRevenue = profile.businessMonthlyRevenue();
        this.updatedAt = Instant.now();
    }

    public AppUserEntity getUser() {
        return user;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UserProfile toDomain() {
        return new UserProfile(
                birthDate, regionCode, employmentStatus, studentStatus,
                personalMonthlyIncome, householdMonthlyIncome, medianIncomeRatio,
                weeklyWorkHours, householdSize, householdType, housingType,
                homeOwnership, asset, maritalStatus, childrenCount, businessStatus,
                businessAgeMonths, businessMonthlyRevenue
        );
    }
}
