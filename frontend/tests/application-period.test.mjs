import assert from "node:assert/strict";
import { test } from "node:test";
import { applicationPeriod } from "../src/lib/policy-display.ts";

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
    assert.equal(applicationPeriod(withoutDates(value)), "신청기간 확인 필요", value);
  }
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
