"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../../lib/api";
import {
  BUSINESS_STATUS_OPTIONS,
  EMPLOYMENT_STATUS_OPTIONS,
  HOME_OWNERSHIP_OPTIONS,
  HOUSEHOLD_TYPE_OPTIONS,
  HOUSING_TYPE_OPTIONS,
  MARITAL_STATUS_OPTIONS,
  REGION_OPTIONS,
  STUDENT_STATUS_OPTIONS,
} from "../../lib/policy-display";
import { loadProfile, saveProfile } from "../../lib/profile-storage";
import type {
  BusinessStatus,
  EmploymentStatus,
  HomeOwnership,
  MaritalStatus,
  RegionCode,
  StudentStatus,
  UserProfile,
} from "../../lib/types";

const emptyProfile: UserProfile = {
  birthDate: null,
  regionCode: null,
  employmentStatus: null,
};

function optionalNumber(value: string): number | null {
  return value === "" ? null : Number(value);
}

function optionalSelection<T extends string>(value: string): T | null {
  return value === "" ? null : value as T;
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile>(emptyProfile);
  const [showDetails, setShowDetails] = useState(false);
  const [saved, setSaved] = useState(false);
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const stored = loadProfile();
    apiFetch<{ authenticated: boolean }>("/api/v1/auth/me")
      .then(async (session) => {
        setAuthenticated(session.authenticated);
        if (!session.authenticated) {
          if (stored) setProfile(stored);
          return;
        }
        const serverProfile = await apiFetch<UserProfile>("/api/v1/me/profile");
        const hasServerValue = Object.values(serverProfile).some((value) => value !== null && value !== "");
        setProfile(hasServerValue ? serverProfile : stored ?? serverProfile);
      })
      .catch(() => {
        setAuthenticated(false);
        if (stored) setProfile(stored);
      });
  }, []);

  function update<K extends keyof UserProfile>(key: K, value: UserProfile[K]) {
    setSaved(false);
    setMessage(null);
    setProfile((current) => ({ ...current, [key]: value }));
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    saveProfile(profile);
    try {
      if (authenticated) {
        const persisted = await apiFetch<UserProfile>("/api/v1/me/profile", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(profile),
        });
        setProfile(persisted);
        saveProfile(persisted);
        setMessage("프로필을 저장했습니다.");
      } else {
        setMessage("브라우저에만 임시 저장했습니다. 분석하려면 로그인해 주세요.");
      }
      setSaved(true);
    } catch (caught) {
      setMessage(caught instanceof ApiError && caught.status === 401
        ? "로그인 정보가 만료되었습니다. 다시 로그인해 주세요."
        : "프로필을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  return (
    <main className="page-shell shell narrow-page">
      <header className="page-heading">
        <span className="eyebrow">02 — PROFILE</span>
        <h1>내 조건을<br />알려주세요.</h1>
        <p>처음에는 꼭 필요한 정보만 입력하세요. 모르는 항목은 비워두어도 탈락으로 판단하지 않습니다.</p>
      </header>

      <form className="profile-form" onSubmit={submit}>
        <section className="form-section">
          <div className="section-heading">
            <span>01</span>
            <div><h2>기본 정보</h2><p>연령, 지역, 현재 상태를 우선 사용합니다.</p></div>
          </div>
          <div className="form-grid">
            <label>생년월일<input type="date" value={profile.birthDate ?? ""} onChange={(event) => update("birthDate", event.target.value || null)} /></label>
            <label>거주지역<select value={profile.regionCode ?? ""} onChange={(event) => update("regionCode", optionalSelection<RegionCode>(event.target.value))}>
              <option value="">선택하지 않음</option>
              {REGION_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
            </select></label>
            <label>현재 상태<select value={profile.employmentStatus ?? ""} onChange={(event) => update("employmentStatus", optionalSelection<EmploymentStatus>(event.target.value))}>
              <option value="">선택하지 않음</option>
              {EMPLOYMENT_STATUS_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
            </select></label>
            <label>학생 상태<select value={profile.studentStatus ?? ""} onChange={(event) => update("studentStatus", optionalSelection<StudentStatus>(event.target.value))}>
              <option value="">선택하지 않음</option>
              {STUDENT_STATUS_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
            </select></label>
          </div>
        </section>

        <button className="details-toggle" type="button" onClick={() => setShowDetails((current) => !current)}>
          {showDetails ? "추가 정보 접기" : "정확도를 높일 추가 정보 입력"}<span>{showDetails ? "−" : "+"}</span>
        </button>

        {showDetails && (
          <section className="form-section optional-section">
            <div className="section-heading">
              <span>02</span>
              <div><h2>추가 정보</h2><p>비워둔 항목은 추가 정보가 필요한 항목으로 안내됩니다.</p></div>
            </div>
            <div className="form-grid">
              <label>월 개인소득<input min="0" type="number" value={profile.personalMonthlyIncome ?? ""} onChange={(event) => update("personalMonthlyIncome", optionalNumber(event.target.value))} /></label>
              <label>월 가구소득<input min="0" type="number" value={profile.householdMonthlyIncome ?? ""} onChange={(event) => update("householdMonthlyIncome", optionalNumber(event.target.value))} /></label>
              <label>기준중위소득 비율<input min="0" type="number" value={profile.medianIncomeRatio ?? ""} onChange={(event) => update("medianIncomeRatio", optionalNumber(event.target.value))} /></label>
              <label>주당 근로시간<input min="0" max="168" type="number" value={profile.weeklyWorkHours ?? ""} onChange={(event) => update("weeklyWorkHours", optionalNumber(event.target.value))} /></label>
              <label>가구원 수<input min="1" type="number" value={profile.householdSize ?? ""} onChange={(event) => update("householdSize", optionalNumber(event.target.value))} /></label>
              <label>가구 형태<select value={profile.householdType ?? ""} onChange={(event) => update("householdType", event.target.value || null)}>
                <option value="">선택하지 않음</option>
                {HOUSEHOLD_TYPE_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select></label>
              <label>주거 형태<select value={profile.housingType ?? ""} onChange={(event) => update("housingType", event.target.value || null)}>
                <option value="">선택하지 않음</option>
                {HOUSING_TYPE_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select></label>
              <label>주택 보유<select value={profile.homeOwnership ?? ""} onChange={(event) => update("homeOwnership", optionalSelection<HomeOwnership>(event.target.value))}>
                <option value="">선택하지 않음</option>
                {HOME_OWNERSHIP_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select></label>
              <label>보유 자산<input min="0" type="number" value={profile.asset ?? ""} onChange={(event) => update("asset", optionalNumber(event.target.value))} /></label>
              <label>혼인 상태<select value={profile.maritalStatus ?? ""} onChange={(event) => update("maritalStatus", optionalSelection<MaritalStatus>(event.target.value))}>
                <option value="">선택하지 않음</option>
                {MARITAL_STATUS_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select></label>
              <label>자녀 수<input min="0" type="number" value={profile.childrenCount ?? ""} onChange={(event) => update("childrenCount", optionalNumber(event.target.value))} /></label>
              <label>사업 상태<select value={profile.businessStatus ?? ""} onChange={(event) => update("businessStatus", optionalSelection<BusinessStatus>(event.target.value))}>
                <option value="">선택하지 않음</option>
                {BUSINESS_STATUS_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select></label>
              <label>사업 기간(개월)<input min="0" type="number" value={profile.businessAgeMonths ?? ""} onChange={(event) => update("businessAgeMonths", optionalNumber(event.target.value))} /></label>
              <label>월 사업 매출<input min="0" type="number" value={profile.businessMonthlyRevenue ?? ""} onChange={(event) => update("businessMonthlyRevenue", optionalNumber(event.target.value))} /></label>
            </div>
          </section>
        )}

        <div className="form-actions">
          <button className="primary" type="submit">프로필 저장</button>
          {message && <span className={saved ? "saved-message" : "error"}>{message}</span>}
          {authenticated ? <Link className="secondary-link" href="/analysis">자격 분석으로</Link> : <Link className="secondary-link" href="/auth">로그인하기</Link>}
        </div>
        <p className="privacy-note">입력한 정보는 맞춤 분석을 위해 저장되며, 소득·자산의 원문은 분석 기록에 남기지 않습니다.</p>
      </form>
    </main>
  );
}

