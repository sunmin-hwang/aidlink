export type EvaluationStatus = "PASS" | "FAIL" | "UNKNOWN";
export type EligibilityStatus = "ELIGIBLE" | "NOT_ELIGIBLE" | "UNKNOWN" | "NEAR_MISS";
export type RegionCode =
  | "SEOUL"
  | "BUSAN"
  | "DAEGU"
  | "INCHEON"
  | "GWANGJU"
  | "DAEJEON"
  | "ULSAN"
  | "SEJONG"
  | "GYEONGGI"
  | "GANGWON"
  | "CHUNGBUK"
  | "CHUNGNAM"
  | "JEONBUK"
  | "JEONNAM"
  | "GYEONGBUK"
  | "GYEONGNAM"
  | "JEJU";
export type EmploymentStatus =
  | "STUDENT"
  | "JOB_SEEKER"
  | "EMPLOYED"
  | "SELF_EMPLOYED"
  | "UNEMPLOYED"
  | "RETIRED"
  | "OTHER";
export type StudentStatus = "ENROLLED" | "LEAVE_OF_ABSENCE" | "GRADUATED" | "NOT_STUDENT";
export type HomeOwnership = "NONE" | "OWNED_BY_SELF" | "OWNED_BY_HOUSEHOLD";
export type MaritalStatus = "SINGLE" | "MARRIED" | "DIVORCED" | "WIDOWED";
export type BusinessStatus = "NONE" | "PREPARING" | "ACTIVE" | "CLOSED";
export type SimulationChangeStatus =
  | "NEWLY_ELIGIBLE"
  | "LOST_ELIGIBILITY"
  | "UNCHANGED_ELIGIBLE"
  | "UNCHANGED_NOT_ELIGIBLE"
  | "UNKNOWN_CHANGED";

export type UserProfile = {
  birthDate?: string | null;
  regionCode?: RegionCode | null;
  employmentStatus?: EmploymentStatus | null;
  studentStatus?: StudentStatus | null;
  personalMonthlyIncome?: number | null;
  householdMonthlyIncome?: number | null;
  medianIncomeRatio?: number | null;
  weeklyWorkHours?: number | null;
  householdSize?: number | null;
  householdType?: string | null;
  housingType?: string | null;
  homeOwnership?: HomeOwnership | null;
  asset?: number | null;
  maritalStatus?: MaritalStatus | null;
  childrenCount?: number | null;
  businessStatus?: BusinessStatus | null;
  businessAgeMonths?: number | null;
  businessMonthlyRevenue?: number | null;
};

export type Policy = {
  lifecycleStatus?: "ACTIVE" | "EXPIRED" | "SOURCE_REMOVED" | "ARCHIVED";
  applicationPeriod?: {
    kind: "UNKNOWN" | "YEAR_ROUND" | "FIXED";
    sourceText: string;
    startTime: string | null;
    endTime: string | null;
    origin: "API" | "PROVIDER_REVIEW";
    verifiedSourceUrl: string | null;
    verifiedAt: string | null;
    reviewRequired: boolean;
  };
  id: number;
  externalId: string;
  name: string;
  provider: string;
  category: string;
  regionCode: string;
  description: string;
  targetDescription: string;
  supportContent: string;
  applicationStart: string | null;
  applicationEnd: string | null;
  applicationMethod: string;
  requiredDocuments: string;
  applicationUrl: string;
  sourceUrl: string;
  sourceUpdatedAt: string | null;
  analysisStatus: string;
  rule: RuleNode | null;
};

export type PolicySearchSort = "relevance" | "deadline" | "recent" | "name";

export type PolicySearchPage = {
  content: Policy[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  sort: PolicySearchSort;
  first: boolean;
  last: boolean;
};

export type PolicyBookmarkPage = {
  content: Policy[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type RuleNode = {
  type: "AND" | "OR" | "CONDITION";
  children?: RuleNode[];
  field?: string;
  operator?: string;
  expectedValues?: string[];
  description?: string;
};

export type Condition = {
  field: string;
  operator: string;
  expectedValues: string[];
  actualValue: string | null;
  status: EvaluationStatus;
  description: string;
  message: string;
};

export type Analysis = {
  policyId: number;
  policyName: string;
  provider: string;
  category: string;
  analysisStatus: string;
  eligibilityStatus: EligibilityStatus;
  ruleStatus: EvaluationStatus;
  conditions: Condition[];
  blockingConditions: Condition[];
  guidance: string;
};

export type EligibilityAnalysisSort = "recommended" | "missing_count" | "deadline" | "name";

export type EligibilityAnalysisPage = {
  content: Analysis[];
  summary: Record<EligibilityStatus, number>;
  categories: string[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  sort: EligibilityAnalysisSort;
  status: EligibilityStatus | null;
  first: boolean;
  last: boolean;
};

export type SimulationPolicyResult = {
  policyId: number;
  policyName: string;
  changeStatus: SimulationChangeStatus;
  current: Analysis;
  simulated: Analysis;
};

export type SimulationSort = "importance" | "name";

export type SimulationResult = {
  content: SimulationPolicyResult[];
  summary: Record<SimulationChangeStatus, number>;
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  sort: SimulationSort;
  status: SimulationChangeStatus | null;
  first: boolean;
  last: boolean;
};

