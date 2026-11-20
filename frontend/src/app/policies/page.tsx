import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApiBaseUrl } from "../../lib/api";
import {
  applicationPeriod,
  EMPLOYMENT_STATUS_OPTIONS,
  HOUSEHOLD_TYPE_OPTIONS,
  REGION_OPTIONS,
  regionLabel,
  summarizePolicyText,
} from "../../lib/policy-display";
import type { PolicySearchPage, PolicySearchSort } from "../../lib/types";
import { paginationItems } from "../../lib/pagination";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "지원정책 검색 | AIDLINK",
  description: "정부·지자체·공공기관의 지원정책을 검색하고 공식 출처를 확인하세요.",
};

type SearchParams = Promise<Record<string, string | string[] | undefined>>;
type PolicyLoadResult = { result: PolicySearchPage | null; failed: boolean };

const FILTER_NAMES = [
  "q", "category", "region", "provider", "age", "employment",
  "householdType", "openOnly", "sort",
] as const;

const SORT_OPTIONS: Array<{ value: PolicySearchSort; label: string }> = [
  { value: "relevance", label: "관련도순" },
  { value: "deadline", label: "마감 임박순" },
  { value: "recent", label: "최근 갱신순" },
  { value: "name", label: "정책명 가나다순" },
];

async function loadPolicies(params: Record<string, string | string[] | undefined>): Promise<PolicyLoadResult> {
  try {
    const query = new URLSearchParams();
    FILTER_NAMES.forEach((name) => {
      const value = first(params[name]);
      if (value) query.set(name, value);
    });
    query.set("page", String(parseRequestedPage(first(params.page)) - 1));
    query.set("size", "10");
    const response = await fetch(`${serverApiBaseUrl}/api/v1/policies/search?${query}`, { cache: "no-store" });
    return response.ok
      ? { result: (await response.json()) as PolicySearchPage, failed: false }
      : { result: null, failed: true };
  } catch {
    return { result: null, failed: true };
  }
}

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function parseRequestedPage(value: string): number {
  if (!/^\d+$/.test(value)) return 1;
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : 1;
}

function selectedSort(value: string): string {
  return SORT_OPTIONS.some((option) => option.value === value) ? value : "";
}

function policiesHref(
  params: Record<string, string | string[] | undefined>,
  page: number,
): string {
  const query = new URLSearchParams();
  FILTER_NAMES.forEach((name) => {
    const value = first(params[name]);
    if (value) query.set(name, value);
  });
  if (page > 1) query.set("page", String(page));
  const serialized = query.toString();
  return serialized ? `/policies?${serialized}` : "/policies";
}

export default async function PoliciesPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const requestedPage = parseRequestedPage(first(params.page));
  const { result, failed } = await loadPolicies(params);
  const normalizedPage = result && result.totalPages > 0 ? result.page + 1 : 1;
  if (!failed && result && first(params.page) && requestedPage !== normalizedPage) {
    redirect(policiesHref(params, normalizedPage));
  }
  const policies = result?.content ?? [];
  const category = first(params.category);
  const region = first(params.region);

  return (
    <main className="page-shell shell">
      <header className="page-heading">
        <span className="eyebrow">01 — FIND</span>
        <h1>필요한 정책을<br />찾아보세요.</h1>
        <p>로그인 후 무료로 분야와 지역별 정책을 찾고, 지원 내용과 공식 신청 정보를 확인할 수 있습니다.</p>
      </header>

      <form className="search-panel" action="/policies">
        <label>
          검색어
          <input name="q" defaultValue={first(params.q)} placeholder="정책명, 지원내용, 기관명" />
        </label>
        <label>
          분야
          <select name="category" defaultValue={category}>
            <option value="">전체 분야</option>
            <option value="주거">주거</option>
            <option value="취업">취업</option>
            <option value="생활">생활</option>
          </select>
        </label>
        <label>
          지역
          <select name="region" defaultValue={region}>
            <option value="">전체 지역</option>
            {REGION_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label>
          제공기관
          <input name="provider" defaultValue={first(params.provider)} placeholder="기관명" />
        </label>
        <label>
          현재 나이
          <input name="age" min="0" max="120" type="number" defaultValue={first(params.age)} placeholder="예: 29" />
        </label>
        <label>
          취업상태
          <select name="employment" defaultValue={first(params.employment)}>
            <option value="">전체 상태</option>
            {EMPLOYMENT_STATUS_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label>
          가구형태
          <select name="householdType" defaultValue={first(params.householdType)}>
            <option value="">전체 형태</option>
            {HOUSEHOLD_TYPE_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label>
          정렬
          <select name="sort" defaultValue={selectedSort(first(params.sort))}>
            <option value="">기본 정렬</option>
            {SORT_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label className="checkbox-label"><input name="openOnly" type="checkbox" value="true" defaultChecked={first(params.openOnly) === "true"} />현재 신청 가능한 기간만</label>
        <button className="primary compact" type="submit">검색</button>
      </form>

      {!failed && result && <div className="list-heading">
          <strong>{result.totalElements.toLocaleString("ko-KR")}개 정책</strong>
          <span>{result.totalPages > 0 ? `${result.page + 1} / ${result.totalPages} 페이지` : "검색 결과 없음"}</span>
        </div>}

      {failed ? (
        <div className="empty-state">
          <h2>정책을 불러오지 못했습니다.</h2>
          <p>잠시 후 다시 시도해 주세요.</p>
        </div>
      ) : policies.length === 0 ? (
        <div className="empty-state">
          <h2>검색 결과가 없습니다.</h2>
          <p>검색어나 필터를 바꿔 다시 찾아보세요.</p>
        </div>
      ) : (
        <div className="catalog-grid">
          {policies.map((policy) => (
            <Link
              aria-label={`${policy.name} 상세 보기`}
              className="catalog-card"
              href={`/policies/${policy.id}`}
              key={policy.id}
            >
              <div className="catalog-card-topline">
                <div className="catalog-meta">
                  <span>{policy.category}</span>
                  <span>{regionLabel(policy.regionCode)}</span>
                </div>
              </div>
              <h2>{policy.name}</h2>
              <p className="catalog-description">{policy.description}</p>
              <div className="catalog-highlights">
                <div>
                  <span>지원 혜택</span>
                  <p>{summarizePolicyText(policy.supportContent)}</p>
                </div>
                <div>
                  <span>신청 기간</span>
                  <p>{applicationPeriod(policy)}</p>
                </div>
              </div>
              <div className="catalog-footer">
                <span>{policy.provider}</span>
                <strong>자세히 보기 <span aria-hidden="true">→</span></strong>
              </div>
            </Link>
          ))}
        </div>
      )}

      {!failed && result && result.totalPages > 1 && (
        <nav className="pagination" aria-label="정책 검색 결과 페이지">
          {result.first ? (
            <span className="pagination-direction disabled" aria-disabled="true">← 이전</span>
          ) : (
            <Link className="pagination-direction" href={policiesHref(params, result.page)}>← 이전</Link>
          )}
          <div className="pagination-pages">
            {paginationItems(result.page + 1, result.totalPages).map((item) => typeof item === "number" ? (
              item === result.page + 1 ? (
                <span className="pagination-page current" aria-current="page" key={item}>{item}</span>
              ) : (
                <Link
                  aria-label={`${item}페이지로 이동`}
                  className="pagination-page"
                  href={policiesHref(params, item)}
                  key={item}
                  prefetch={false}
                >{item}</Link>
              )
            ) : (
              <span className="pagination-ellipsis" aria-hidden="true" key={item}>…</span>
            ))}
          </div>
          {result.last ? (
            <span className="pagination-direction disabled" aria-disabled="true">다음 →</span>
          ) : (
            <Link className="pagination-direction" href={policiesHref(params, result.page + 2)}>다음 →</Link>
          )}
        </nav>
      )}
    </main>
  );
}

