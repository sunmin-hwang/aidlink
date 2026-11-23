import assert from "node:assert/strict";
import { test } from "node:test";
import { applicationPeriod, applicationPeriodNotice, applicationMethodDescription } from "../src/lib/policy-display.ts";

const withoutDates = (applicationMethod = "") => ({ applicationStart: null, applicationEnd: null, applicationMethod });

test("missing dates do not imply always-open applications", () => {
  for (const value of ["", "정보 없음", "신청기한:", "신청기한: 정보 없음", "신청기한: 해당없음"]) {
    assert.equal(applicationPeriod(withoutDates(value)), "신청기간 확인 필요", value);
  }
});

test("requires an explicit, unqualified year-round deadline section", () => {
  for (const deadline of ["상시신청", "상시 접수", "연중", "연중 상시 신청"]) {
    assert.equal(applicationPeriod(withoutDates(`방문 신청\r\n\r\n신청기한: ${deadline}\r\n`)), "상시 신청");
  }
  for (const value of ["상시 방문상담 가능", "상시신청은 불가", "신청기한: 상시신청 아님", "신청기한: 상시신청(예산 소진 시까지)", "신청기한: 매년 공고\n상시신청", "○ 설명 안의 신청기한: 상시신청"]) {
    assert.notEqual(applicationPeriod(withoutDates(value)), "상시 신청", value);
    assert.match(applicationPeriodNotice(withoutDates(value)), /공식 원문/);
  }
});

test("distinguishes explicit no-application, agency-specific and separate-announcement guidance", () => {
  for (const text of ["별도의 신청절차가 없음", "개인 신청절차 없음", "신청 불필요", "신청불요"]) {
    const policy = withoutDates(`신청기한: ${text}`);
    assert.equal(applicationPeriod(policy), "별도 신청 없음");
    assert.match(applicationPeriodNotice(policy), /대상·이용 절차/);
  }
  assert.equal(applicationPeriod(withoutDates("신청기한: 접수기관 별 상이")), "기관별 일정 상이");
  assert.equal(applicationPeriod(withoutDates("신청기한: 별도 공고")), "모집 공고 확인 필요");
  // A conditional instruction must not be reduced to unconditional no-application guidance.
  assert.equal(applicationPeriod(withoutDates("신청기한: 기존 대상자는 신청 불필요, 신규 대상자는 1월 신청")), "기존 대상자는 신청 불필요, 신규 대상자는 1월 신청");
});

test("keeps recurring and conditional source guidance without inventing exact dates", () => {
  for (const text of ["매년 1월", "매년 5.1.~5.31.", "해당연도 1~2월(모집기간 별도)", "연초~ 예산 소진시", "상시신청(예산 소진 시까지)"]) {
    const policy = withoutDates(`신청기한: ${text}`);
    assert.equal(applicationPeriod(policy), text);
    assert.match(applicationPeriodNotice(policy), /현재 일정과 다를 수 있습니다/);
  }
});

test("uses dedicated API source text, including an empty source, ahead of legacy instructions", () => {
  const policy = { ...withoutDates("신청기한: 상시신청"), applicationPeriod: { kind: "UNKNOWN", sourceText: "매년 1월" } };
  assert.equal(applicationPeriod(policy), "매년 1월");
  assert.equal(applicationPeriod({ ...policy, applicationPeriod: { ...policy.applicationPeriod, sourceText: "" } }), "신청기간 확인 필요");
  assert.equal(applicationPeriodNotice(withoutDates("신청기한: 상시신청")), null);
  assert.equal(applicationPeriodNotice({ ...withoutDates(), applicationEnd: "2026-12-31" }), null);
});

test("structured dates take precedence over source prose", () => {
  assert.equal(applicationPeriod({ ...withoutDates("신청기한: 상시신청"), applicationStart: "2026-05-01", applicationEnd: "2026-06-01" }), "2026. 5. 1. ~ 2026. 6. 1.");
  assert.equal(applicationPeriod({ ...withoutDates(), applicationEnd: "2026-06-01" }), "2026. 6. 1.까지");
  assert.equal(applicationPeriod({ ...withoutDates(), applicationStart: "2026-05-01" }), "2026. 5. 1.부터 · 종료일 확인 필요");
});

test("invalid or reversed dates require confirmation without a year-round fallback", () => {
  for (const dates of [
    { applicationStart: "2026-06-01", applicationEnd: "2026-05-01" },
    { applicationStart: "2026-02-30", applicationEnd: null },
    { applicationStart: null, applicationEnd: "not-a-date" },
  ]) assert.equal(applicationPeriod({ ...withoutDates("신청기한: 상시신청"), ...dates }), "신청기간 확인 필요");
  assert.equal(applicationPeriod({ ...withoutDates(), applicationStart: "2024-02-29" }), "2024. 2. 29.부터 · 종료일 확인 필요");
});

test("verified extension shows effective dates, known time and expiry without using the older API deadline", () => {
  const policy = { applicationStart: "2026-10-07", applicationEnd: "2026-10-20", lifecycleStatus: "EXPIRED",
    applicationPeriod: { kind: "FIXED", sourceText: "2026.10.07~2026.10.15", startTime: "10:00:00", endTime: null, origin: "PROVIDER_REVIEW", verifiedAt: "2026-11-23T00:00:00Z", verifiedSourceUrl: "https://provider.or.kr/period", reviewRequired: false } };
  assert.equal(applicationPeriod(policy), "신청 종료 · 2026. 10. 7. 10:00 ~ 2026. 10. 20.");
  assert.equal(applicationPeriod({ ...policy, applicationPeriod: { ...policy.applicationPeriod, reviewRequired: true } }), "신청 종료 · 2026. 10. 7. 10:00 ~ 2026. 10. 20. · 공식 안내 재확인 필요");
});

test("application instructions do not repeat the stale API deadline as current instructions", () => {
  assert.equal(applicationMethodDescription("온라인 신청\r\n\r\n신청기한: 2026.10.07~2026.10.15"), "온라인 신청");
  assert.equal(applicationMethodDescription("방문 또는 전화 상담"), "방문 또는 전화 상담");
});
