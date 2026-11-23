import type {
  BusinessStatus,
  EligibilityStatus,
  EmploymentStatus,
  EvaluationStatus,
  HomeOwnership,
  MaritalStatus,
  Policy,
  RegionCode,
  SimulationChangeStatus,
  StudentStatus,
} from "./types";

type DisplayOption<T extends string> = Readonly<{ value: T; label: string }>;

export const REGION_OPTIONS = [
  { value: "SEOUL", label: "서울특별시" },
  { value: "BUSAN", label: "부산광역시" },
  { value: "DAEGU", label: "대구광역시" },
  { value: "INCHEON", label: "인천광역시" },
  { value: "GWANGJU", label: "광주광역시" },
  { value: "DAEJEON", label: "대전광역시" },
  { value: "ULSAN", label: "울산광역시" },
  { value: "SEJONG", label: "세종특별자치시" },
  { value: "GYEONGGI", label: "경기도" },
  { value: "GANGWON", label: "강원특별자치도" },
  { value: "CHUNGBUK", label: "충청북도" },
  { value: "CHUNGNAM", label: "충청남도" },
  { value: "JEONBUK", label: "전북특별자치도" },
  { value: "JEONNAM", label: "전라남도" },
  { value: "GYEONGBUK", label: "경상북도" },
  { value: "GYEONGNAM", label: "경상남도" },
  { value: "JEJU", label: "제주특별자치도" },
] as const satisfies readonly DisplayOption<RegionCode>[];

export const EMPLOYMENT_STATUS_OPTIONS = [
  { value: "STUDENT", label: "학생" },
  { value: "JOB_SEEKER", label: "취업준비 중" },
  { value: "EMPLOYED", label: "재직 중" },
  { value: "SELF_EMPLOYED", label: "자영업 중" },
  { value: "UNEMPLOYED", label: "미취업·실업" },
  { value: "RETIRED", label: "퇴직" },
  { value: "OTHER", label: "기타" },
] as const satisfies readonly DisplayOption<EmploymentStatus>[];

export const STUDENT_STATUS_OPTIONS = [
  { value: "ENROLLED", label: "재학" },
  { value: "LEAVE_OF_ABSENCE", label: "휴학" },
  { value: "GRADUATED", label: "졸업" },
  { value: "NOT_STUDENT", label: "학생 아님" },
] as const satisfies readonly DisplayOption<StudentStatus>[];

export const HOUSEHOLD_TYPE_OPTIONS = [
  { value: "SINGLE", label: "1인 가구" },
  { value: "COUPLE", label: "부부" },
  { value: "WITH_PARENTS", label: "부모와 동거" },
  { value: "WITH_CHILDREN", label: "자녀와 동거" },
] as const;

export const HOUSING_TYPE_OPTIONS = [
  { value: "WITH_PARENTS", label: "부모와 거주" },
  { value: "RENT", label: "임차" },
  { value: "OWNED", label: "자가" },
  { value: "DORMITORY", label: "기숙사" },
] as const;

export const HOME_OWNERSHIP_OPTIONS = [
  { value: "NONE", label: "무주택" },
  { value: "OWNED_BY_SELF", label: "본인 소유" },
  { value: "OWNED_BY_HOUSEHOLD", label: "가구원 소유" },
] as const satisfies readonly DisplayOption<HomeOwnership>[];

export const MARITAL_STATUS_OPTIONS = [
  { value: "SINGLE", label: "미혼" },
  { value: "MARRIED", label: "기혼" },
  { value: "DIVORCED", label: "이혼" },
  { value: "WIDOWED", label: "사별" },
] as const satisfies readonly DisplayOption<MaritalStatus>[];

export const BUSINESS_STATUS_OPTIONS = [
  { value: "NONE", label: "해당 없음" },
  { value: "PREPARING", label: "창업 준비 중" },
  { value: "ACTIVE", label: "사업 중" },
  { value: "CLOSED", label: "폐업" },
] as const satisfies readonly DisplayOption<BusinessStatus>[];

const REGION_LABELS: Record<RegionCode | "NATIONWIDE", string> = {
  NATIONWIDE: "전국",
  ...Object.fromEntries(REGION_OPTIONS.map(({ value, label }) => [value, label])) as Record<RegionCode, string>,
};

const EMPLOYMENT_STATUS_LABELS = Object.fromEntries(
  EMPLOYMENT_STATUS_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<EmploymentStatus, string>;
const STUDENT_STATUS_LABELS = Object.fromEntries(
  STUDENT_STATUS_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<StudentStatus, string>;
const HOUSEHOLD_TYPE_LABELS = Object.fromEntries(
  HOUSEHOLD_TYPE_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<string, string>;
const HOUSING_TYPE_LABELS = Object.fromEntries(
  HOUSING_TYPE_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<string, string>;
const HOME_OWNERSHIP_LABELS = Object.fromEntries(
  HOME_OWNERSHIP_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<HomeOwnership, string>;
const MARITAL_STATUS_LABELS = Object.fromEntries(
  MARITAL_STATUS_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<MaritalStatus, string>;
const BUSINESS_STATUS_LABELS = Object.fromEntries(
  BUSINESS_STATUS_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<BusinessStatus, string>;

const ELIGIBILITY_LABELS: Record<EligibilityStatus, string> = {
  ELIGIBLE: "신청 가능",
  NEAR_MISS: "거의 가능",
  UNKNOWN: "추가 정보 필요",
  NOT_ELIGIBLE: "현재 신청 어려움",
};

const EVALUATION_LABELS: Record<EvaluationStatus, string> = {
  PASS: "충족",
  FAIL: "미충족",
  UNKNOWN: "추가 확인 필요",
};

const SIMULATION_CHANGE_LABELS: Record<SimulationChangeStatus, string> = {
  NEWLY_ELIGIBLE: "새롭게 가능",
  LOST_ELIGIBILITY: "자격 상실",
  UNCHANGED_ELIGIBLE: "계속 가능",
  UNCHANGED_NOT_ELIGIBLE: "변화 없음",
  UNKNOWN_CHANGED: "정보 상태 변화",
};

export function regionLabel(value: string): string {
  return REGION_LABELS[value as keyof typeof REGION_LABELS] ?? "지역 정보 확인 필요";
}

export function employmentStatusLabel(value: string): string {
  return EMPLOYMENT_STATUS_LABELS[value as EmploymentStatus] ?? "현재 상태 확인 필요";
}

export function studentStatusLabel(value: string): string {
  return STUDENT_STATUS_LABELS[value as StudentStatus] ?? "학생 상태 확인 필요";
}

export function householdTypeLabel(value: string): string {
  return HOUSEHOLD_TYPE_LABELS[value] ?? "가구 형태 확인 필요";
}

export function housingTypeLabel(value: string): string {
  return HOUSING_TYPE_LABELS[value] ?? "주거 형태 확인 필요";
}

export function homeOwnershipLabel(value: string): string {
  return HOME_OWNERSHIP_LABELS[value as HomeOwnership] ?? "주택 보유 상태 확인 필요";
}

export function maritalStatusLabel(value: string): string {
  return MARITAL_STATUS_LABELS[value as MaritalStatus] ?? "혼인 상태 확인 필요";
}

export function businessStatusLabel(value: string): string {
  return BUSINESS_STATUS_LABELS[value as BusinessStatus] ?? "사업 상태 확인 필요";
}

export function eligibilityLabel(value: EligibilityStatus): string {
  return ELIGIBILITY_LABELS[value];
}

export function evaluationLabel(value: EvaluationStatus): string {
  return EVALUATION_LABELS[value];
}

export function simulationChangeLabel(value: SimulationChangeStatus): string {
  return SIMULATION_CHANGE_LABELS[value];
}

export function eligibilityGuidance(value: EligibilityStatus): string {
  switch (value) {
    case "ELIGIBLE":
      return "현재 입력한 조건과 맞습니다. 신청 전 공식 안내에서 최종 자격과 기간을 확인하세요.";
    case "NEAR_MISS":
      return "부족한 조건이 많지 않습니다. 아래 항목과 공식 안내를 확인해 보세요.";
    case "UNKNOWN":
      return "판정에 필요한 정보가 더 있습니다. 아래 항목을 보완한 뒤 다시 분석해 보세요.";
    case "NOT_ELIGIBLE":
      return "현재 입력한 조건에서는 신청이 어렵습니다. 부족한 조건과 다른 정책을 함께 확인하세요.";
  }
}

export function formatPolicyDate(value: string | null): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(`${value}T00:00:00Z`);
  if (!year || date.getUTCFullYear() !== year || date.getUTCMonth() + 1 !== month
      || date.getUTCDate() !== day) return null;
  return `${year}. ${month}. ${day}.`;
}

export type ApplicationPeriodInput = Pick<Policy, "applicationStart" | "applicationEnd">
  & Partial<Pick<Policy, "applicationMethod" | "applicationPeriod" | "lifecycleStatus">>;

// Only the collector's dedicated deadline section is evidence for year-round intake.
// Other instructions may mention an always-open office while recruitment is limited.
const YEAR_ROUND_DEADLINES = new Set([
  "상시", "상시신청", "상시접수", "연중", "연중신청", "연중접수",
  "연중상시", "연중상시신청", "연중상시접수",
]);

export function applicationPeriod(policy: ApplicationPeriodInput): string {
  const period = policy.applicationPeriod;
  const startDate = formatPolicyDate(policy.applicationStart);
  const endDate = formatPolicyDate(policy.applicationEnd);
  const start = startDate && `${startDate}${period?.startTime ? ` ${period.startTime.slice(0, 5)}` : ""}`;
  const end = endDate && `${endDate}${period?.endTime ? ` ${period.endTime.slice(0, 5)}` : ""}`;

  if ((policy.applicationStart && !start) || (policy.applicationEnd && !end)
      || (policy.applicationStart && policy.applicationEnd && policy.applicationStart > policy.applicationEnd)) {
    return "신청기간 확인 필요";
  }
  if (!start && !end) {
    if (period) return period.kind === "YEAR_ROUND" ? "상시 신청" : "신청기간 확인 필요";
    const deadline = policy.applicationMethod?.replace(/\r\n?/g, "\n")
      .match(/(?:^|\n)신청기한:[ \t]*([\s\S]*)$/)?.[1].replace(/\s/g, "");
    return deadline && YEAR_ROUND_DEADLINES.has(deadline) ? "상시 신청" : "신청기간 확인 필요";
  }
  const range = !start ? `${end}까지` : !end ? `${start}부터 · 종료일 확인 필요` : `${start} ~ ${end}`;
  return `${policy.lifecycleStatus === "EXPIRED" ? "신청 종료 · " : ""}${range}${period?.reviewRequired ? " · 공식 안내 재확인 필요" : ""}`;
}

export function applicationMethodDescription(value: string): string {
  return value.replace(/\r\n?/g, "\n").replace(/(?:^|\n)신청기한:[\s\S]*$/, "").trim()
    || "신청방법은 제공기관 안내를 확인해 주세요.";
}

export function summarizePolicyText(value: string, maxLength = 82): string {
  const normalized = value
    .replace(/\s*[○ㆍ•∙▪]\s*/g, " · ")
    .replace(/\s+-\s+/g, " · ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^(?:지원(?:대상|내용)\s*:\s*(?:·\s*)?)+/, "")
    .replace(/^·\s*|\s*·$/g, "")
    .trim();
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, maxLength).trimEnd()}…`;
}

export function splitPolicyText(value: string): string[] {
  const normalized = value.replace(/\r/g, "").trim();
  if (!normalized) return [];

  return normalized
    .split(/\n+|\s*[•∙▪○ㆍ]\s*|\s*[;；]\s*|(?<=다\.)\s+(?=[가-힣A-Za-z0-9])/)
    .map((item) => item.replace(/^[\s\u200B\uFEFF\-–—*○ㆍ▪•∙]+/u, "").trim())
    .filter(Boolean);
}
