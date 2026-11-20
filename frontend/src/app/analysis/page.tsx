"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AnalysisCard } from "../../components/AnalysisCard";
import { apiFetch, ApiError } from "../../lib/api";
import { paginationItems } from "../../lib/pagination";
import { eligibilityLabel, REGION_OPTIONS } from "../../lib/policy-display";
import { loadEffectiveProfile } from "../../lib/profile-storage";
import type {
  EligibilityAnalysisPage,
  EligibilityAnalysisSort,
  EligibilityStatus,
  UserProfile,
} from "../../lib/types";

const statusOrder: EligibilityStatus[] = ["ELIGIBLE", "NEAR_MISS", "UNKNOWN", "NOT_ELIGIBLE"];

const sortOptions: Array<{ value: EligibilityAnalysisSort; label: string }> = [
  { value: "recommended", label: "추천순" },
  { value: "missing_count", label: "부족 조건 적은 순" },
  { value: "deadline", label: "마감 임박순" },
  { value: "name", label: "정책명 가나다순" },
];

type AnalysisFilters = {
  status: "" | EligibilityStatus;
  category: string;
  region: string;
  sort: EligibilityAnalysisSort;
};

const initialFilters: AnalysisFilters = {
  status: "",
  category: "",
  region: "",
  sort: "recommended",
};

export default function AnalysisPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [result, setResult] = useState<EligibilityAnalysisPage | null>(null);
  const [filters, setFilters] = useState<AnalysisFilters>(initialFilters);
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

  async function loadAnalysis(targetPage: number, targetFilters: AnalysisFilters) {
    if (!profile) return;
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError(null);
    const query = new URLSearchParams({
      page: String(targetPage - 1),
      size: "20",
      sort: targetFilters.sort,
    });
    if (targetFilters.status) query.set("status", targetFilters.status);
    if (targetFilters.category) query.set("category", targetFilters.category);
    if (targetFilters.region) query.set("region", targetFilters.region);

    try {
      const analysisPage = await apiFetch<EligibilityAnalysisPage>(
        `/api/v1/eligibility/analyze?${query}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ profile }),
        },
      );
      if (sequence === requestSequence.current) setResult(analysisPage);
    } catch (caught) {
      if (sequence !== requestSequence.current) return;
      setError(caught instanceof ApiError && caught.status === 401
        ? "로그인 후 자격 분석을 이용할 수 있습니다."
        : "분석 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }

  function updateFilter<Key extends keyof AnalysisFilters>(
    key: Key,
    value: AnalysisFilters[Key],
  ) {
    const nextFilters = { ...filters, [key]: value };
    setFilters(nextFilters);
    if (result) void loadAnalysis(1, nextFilters);
  }

  if (loaded && !profile) {
    return (
      <main className="page-shell shell"><div className="empty-state">
        <h1>먼저 프로필을 입력해 주세요.</h1><p>모르는 항목은 비워두어도 탈락으로 판단하지 않고 추가 확인 정보로 남겨둡니다.</p>
        <Link className="primary-link" href="/profile">프로필 입력하기</Link>
      </div></main>
    );
  }

  return (
    <main className="page-shell shell">
      <header className="page-heading heading-with-action">
        <div><span className="eyebrow">03 — MATCH</span><h1>내 조건과 맞는<br />정책을 확인하세요.</h1><p>현재 가능한 정책과 부족한 조건, 더 확인할 정보를 이유와 함께 보여드립니다.</p></div>
        <button className="primary" type="button" disabled={!profile || loading} onClick={() => loadAnalysis(1, filters)}>{loading ? "분석 중…" : result ? "다시 분석" : "현재 조건 분석"}</button>
      </header>

      {error && <div className="error-banner">{error} {error.includes("로그인") && <Link href="/auth">로그인하기</Link>}</div>}
      {result ? (
        <>
          <div className="summary-grid analysis-summary">
            {statusOrder.map((status) => <article className={`summary ${status.toLowerCase()}`} key={status}><strong>{(result.summary[status] ?? 0).toLocaleString("ko-KR")}</strong><span>{eligibilityLabel(status)}</span></article>)}
          </div>

          <section className="analysis-controls" aria-label="맞춤 분석 필터와 정렬">
            <label>
              판정 상태
              <select disabled={loading} value={filters.status} onChange={(event) => updateFilter("status", event.target.value as AnalysisFilters["status"])}>
                <option value="">전체 상태</option>
                {statusOrder.map((status) => <option value={status} key={status}>{eligibilityLabel(status)}</option>)}
              </select>
            </label>
            <label>
              분야
              <select disabled={loading} value={filters.category} onChange={(event) => updateFilter("category", event.target.value)}>
                <option value="">전체 분야</option>
                {result.categories.map((category) => <option value={category} key={category}>{category}</option>)}
              </select>
            </label>
            <label>
              지역
              <select disabled={loading} value={filters.region} onChange={(event) => updateFilter("region", event.target.value)}>
                <option value="">전체 지역</option>
                {REGION_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select>
            </label>
            <label>
              정렬
              <select disabled={loading} value={filters.sort} onChange={(event) => updateFilter("sort", event.target.value as EligibilityAnalysisSort)}>
                {sortOptions.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select>
            </label>
          </section>

          <div className="list-heading">
            <strong>{result.totalElements.toLocaleString("ko-KR")}개 분석 결과</strong>
            <span>{result.totalPages > 0 ? `${result.page + 1} / ${result.totalPages} 페이지` : "검색 결과 없음"}</span>
          </div>

          {result.content.length > 0 ? (
            <div className="policy-list analysis-list" data-pending={loading ? "" : undefined}>
              {result.content.map((analysis) => <AnalysisCard analysis={analysis} open={analysis.eligibilityStatus === "NEAR_MISS"} key={analysis.policyId} />)}
            </div>
          ) : (
            <div className="empty-state compact-empty"><h2>조건에 맞는 분석 결과가 없습니다.</h2><p>판정 상태나 분야, 지역 필터를 바꿔보세요.</p></div>
          )}

          {result.totalPages > 1 && (
            <nav className="pagination" aria-label="맞춤 분석 결과 페이지">
              {result.first ? (
                <span className="pagination-direction disabled" aria-disabled="true">← 이전</span>
              ) : (
                <button className="pagination-direction" disabled={loading} type="button" onClick={() => loadAnalysis(result.page, filters)}>← 이전</button>
              )}
              <div className="pagination-pages">
                {paginationItems(result.page + 1, result.totalPages).map((item) => typeof item === "number" ? (
                  item === result.page + 1 ? (
                    <span className="pagination-page current" aria-current="page" key={item}>{item}</span>
                  ) : (
                    <button aria-label={`${item}페이지로 이동`} className="pagination-page" disabled={loading} key={item} type="button" onClick={() => loadAnalysis(item, filters)}>{item}</button>
                  )
                ) : (
                  <span className="pagination-ellipsis" aria-hidden="true" key={item}>…</span>
                ))}
              </div>
              {result.last ? (
                <span className="pagination-direction disabled" aria-disabled="true">다음 →</span>
              ) : (
                <button className="pagination-direction" disabled={loading} type="button" onClick={() => loadAnalysis(result.page + 2, filters)}>다음 →</button>
              )}
            </nav>
          )}
        </>
      ) : (
        <div className="empty-state compact-empty"><h2>내 조건과 맞는 정책을 확인해 보세요.</h2><p>현재 조건 분석을 누르면 신청 가능성과 더 확인할 정보를 정리해 드립니다.</p></div>
      )}
    </main>
  );
}
