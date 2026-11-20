"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiFetch, ApiError } from "../../lib/api";
import { paginationItems } from "../../lib/pagination";
import { eligibilityLabel, REGION_OPTIONS, simulationChangeLabel } from "../../lib/policy-display";
import { loadEffectiveProfile } from "../../lib/profile-storage";
import type {
  RegionCode,
  SimulationChangeStatus,
  SimulationResult,
  SimulationSort,
  UserProfile,
} from "../../lib/types";

const changeStatuses: SimulationChangeStatus[] = [
  "NEWLY_ELIGIBLE",
  "LOST_ELIGIBILITY",
  "UNCHANGED_ELIGIBLE",
  "UNKNOWN_CHANGED",
  "UNCHANGED_NOT_ELIGIBLE",
];

const sortOptions: Array<{ value: SimulationSort; label: string }> = [
  { value: "importance", label: "변화 중요도순" },
  { value: "name", label: "정책명 가나다순" },
];

type EventType = "EMPLOYMENT" | "INCOME" | "INDEPENDENCE" | "MARRIAGE" | "CHILDBIRTH";
type SimulationFilters = { status: "" | SimulationChangeStatus; sort: SimulationSort };

const initialFilters: SimulationFilters = { status: "", sort: "importance" };

export default function SimulationPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [eventType, setEventType] = useState<EventType>("EMPLOYMENT");
  const [income, setIncome] = useState("2500000");
  const [weeklyHours, setWeeklyHours] = useState("40");
  const [region, setRegion] = useState<RegionCode>("SEOUL");
  const [filters, setFilters] = useState<SimulationFilters>(initialFilters);
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestSequence = useRef(0);

  useEffect(() => {
    let active = true;
    loadEffectiveProfile().then((resolved) => {
      if (!active) return;
      setProfile(resolved);
      setLoaded(true);
    });
    return () => { active = false; };
  }, []);

  function changesForEvent(): Partial<UserProfile> {
    switch (eventType) {
      case "EMPLOYMENT": return { employmentStatus: "EMPLOYED", personalMonthlyIncome: Number(income), weeklyWorkHours: Number(weeklyHours) };
      case "INCOME": return { personalMonthlyIncome: Number(income) };
      case "INDEPENDENCE": return { regionCode: region, housingType: "RENT", householdType: "SINGLE" };
      case "MARRIAGE": return { maritalStatus: "MARRIED", householdType: "COUPLE", householdSize: 2 };
      case "CHILDBIRTH": return { maritalStatus: "MARRIED", childrenCount: (profile?.childrenCount ?? 0) + 1 };
    }
  }

  function resetResult() {
    requestSequence.current += 1;
    setResult(null);
    setError(null);
  }

  async function simulate(targetPage = 1, nextFilters = filters) {
    if (!profile) return;
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;
    setLoading(true);
    setError(null);
    const query = new URLSearchParams({
      page: String(Math.max(targetPage - 1, 0)),
      size: "20",
      sort: nextFilters.sort,
    });
    if (nextFilters.status) query.set("status", nextFilters.status);

    try {
      const simulation = await apiFetch<SimulationResult>(`/api/v1/simulations?${query}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentProfile: profile, changes: changesForEvent() }),
      });
      if (requestSequence.current === requestId) setResult(simulation);
    } catch (caught) {
      if (requestSequence.current !== requestId) return;
      setError(caught instanceof ApiError && caught.status === 401
        ? "로그인 후 시뮬레이션을 이용할 수 있습니다."
        : "비교 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (requestSequence.current === requestId) setLoading(false);
    }
  }

  function updateFilter<Key extends keyof SimulationFilters>(key: Key, value: SimulationFilters[Key]) {
    const nextFilters = { ...filters, [key]: value };
    setFilters(nextFilters);
    void simulate(1, nextFilters);
  }

  if (loaded && !profile) {
    return <main className="page-shell shell"><div className="empty-state"><h1>현재 프로필이 필요합니다.</h1><p>비교 기준이 될 현재 조건을 먼저 저장해 주세요.</p><Link className="primary-link" href="/profile">프로필 입력하기</Link></div></main>;
  }

  return (
    <main className="page-shell shell">
      <header className="page-heading"><span className="eyebrow">04 — CHANGE</span><h1>상황이 바뀌면<br />지원도 달라질까요?</h1><p>취업·소득·독립·결혼 같은 변화를 적용해 현재와 달라지는 정책 결과를 미리 비교합니다.</p></header>
      <div className="simulation-layout">
        <section className="simulation-control">
          <h2>어떤 변화가 생기나요?</h2>
          <label>상황 변화<select value={eventType} onChange={(event) => { setEventType(event.target.value as EventType); resetResult(); }}>
            <option value="EMPLOYMENT">취업</option><option value="INCOME">소득 변화</option><option value="INDEPENDENCE">독립·이사</option><option value="MARRIAGE">결혼</option><option value="CHILDBIRTH">출산·자녀 증가</option>
          </select></label>
          {(eventType === "EMPLOYMENT" || eventType === "INCOME") && <label>변경 후 월 개인소득<input min="0" type="number" value={income} onChange={(event) => { setIncome(event.target.value); resetResult(); }} /></label>}
          {eventType === "EMPLOYMENT" && <label>변경 후 주당 근로시간<input min="0" max="168" type="number" value={weeklyHours} onChange={(event) => { setWeeklyHours(event.target.value); resetResult(); }} /></label>}
          {eventType === "INDEPENDENCE" && <label>변경 후 거주지역<select value={region} onChange={(event) => { setRegion(event.target.value as RegionCode); resetResult(); }}>{REGION_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}</select></label>}
          <button className="primary" type="button" disabled={!profile || loading} onClick={() => simulate(1, filters)}>{loading ? "비교 중…" : result ? "다시 비교" : "변경 전후 비교"}</button>
          {error && <p className="error">{error}</p>}
        </section>

        <section className="simulation-results">
          {result ? (
            <>
              <div className="simulation-summary">
                {changeStatuses.map((status) => <article key={status}><strong>{(result.summary[status] ?? 0).toLocaleString("ko-KR")}</strong><span>{simulationChangeLabel(status)}</span></article>)}
              </div>

              <section className="simulation-list-controls" aria-label="상황 변화 필터와 정렬">
                <label>
                  변화 상태
                  <select disabled={loading} value={filters.status} onChange={(event) => updateFilter("status", event.target.value as SimulationFilters["status"])}>
                    <option value="">전체 변화</option>
                    {changeStatuses.map((status) => <option value={status} key={status}>{simulationChangeLabel(status)}</option>)}
                  </select>
                </label>
                <label>
                  정렬
                  <select disabled={loading} value={filters.sort} onChange={(event) => updateFilter("sort", event.target.value as SimulationSort)}>
                    {sortOptions.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
                  </select>
                </label>
              </section>

              <div className="list-heading">
                <strong>{result.totalElements.toLocaleString("ko-KR")}개 변화 결과</strong>
                <span>{result.totalPages > 0 ? `${result.page + 1} / ${result.totalPages} 페이지` : "검색 결과 없음"}</span>
              </div>

              {result.content.length > 0 ? (
                <div className="change-list" data-pending={loading ? "" : undefined}>
                  {result.content.map((policy) => <article className={`change-card ${policy.changeStatus.toLowerCase()}`} key={policy.policyId}>
                    <div><span>{simulationChangeLabel(policy.changeStatus)}</span><h3>{policy.policyName}</h3></div>
                    <p>{eligibilityLabel(policy.current.eligibilityStatus)} <b>→</b> {eligibilityLabel(policy.simulated.eligibilityStatus)}</p>
                  </article>)}
                </div>
              ) : (
                <div className="empty-state compact-empty"><h2>해당하는 변화 결과가 없습니다.</h2><p>변화 상태 필터를 바꿔보세요.</p></div>
              )}

              {result.totalPages > 1 && (
                <nav className="pagination" aria-label="상황 변화 결과 페이지">
                  {result.first ? (
                    <span className="pagination-direction disabled" aria-disabled="true">← 이전</span>
                  ) : (
                    <button className="pagination-direction" disabled={loading} type="button" onClick={() => simulate(result.page, filters)}>← 이전</button>
                  )}
                  <div className="pagination-pages">
                    {paginationItems(result.page + 1, result.totalPages).map((item) => typeof item === "number" ? (
                      item === result.page + 1 ? (
                        <span className="pagination-page current" aria-current="page" key={item}>{item}</span>
                      ) : (
                        <button aria-label={`${item}페이지로 이동`} className="pagination-page" disabled={loading} key={item} type="button" onClick={() => simulate(item, filters)}>{item}</button>
                      )
                    ) : (
                      <span className="pagination-ellipsis" aria-hidden="true" key={item}>…</span>
                    ))}
                  </div>
                  {result.last ? (
                    <span className="pagination-direction disabled" aria-disabled="true">다음 →</span>
                  ) : (
                    <button className="pagination-direction" disabled={loading} type="button" onClick={() => simulate(result.page + 2, filters)}>다음 →</button>
                  )}
                </nav>
              )}
            </>
          ) : <div className="empty-state compact-empty"><h2>변경 결과를 비교해 보세요.</h2><p>상황을 선택하고 비교하면 신청 가능성이 달라지는 정책을 확인할 수 있습니다.</p></div>}
        </section>
      </div>
    </main>
  );
}
