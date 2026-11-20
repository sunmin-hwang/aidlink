"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch, ApiError } from "../../lib/api";
import { paginationItems } from "../../lib/pagination";
import { applicationPeriod, regionLabel, summarizePolicyText } from "../../lib/policy-display";
import type { PolicyBookmarkPage } from "../../lib/types";

const PAGE_SIZE = 12;

export default function BookmarksPage() {
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PolicyBookmarkPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const requestSequence = useRef(0);

  const loadBookmarks = useCallback(async (targetPage: number) => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    try {
      const bookmarkPage = await apiFetch<PolicyBookmarkPage>(
        `/api/v1/me/bookmarks?page=${Math.max(targetPage - 1, 0)}&size=${PAGE_SIZE}`,
      );
      if (requestSequence.current !== requestId) return;
      setResult(bookmarkPage);
      const normalizedPage = bookmarkPage.page + 1;
      if (normalizedPage !== targetPage) setPage(normalizedPage);
    } catch (caught) {
      if (requestSequence.current !== requestId) return;
      setError(caught instanceof ApiError && caught.status === 401
        ? "로그인 후 저장한 정책을 확인할 수 있습니다."
        : "저장한 정책을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (requestSequence.current === requestId) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBookmarks(page);
  }, [loadBookmarks, page]);

  async function removeBookmark(policyId: number) {
    setRemovingId(policyId);
    setError(null);
    try {
      await apiFetch<void>(`/api/v1/me/bookmarks/${policyId}`, { method: "DELETE" });
      await loadBookmarks(page);
    } catch {
      setError("저장 해제에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <main className="page-shell shell">
      <header className="page-heading">
        <span className="eyebrow">02 — SAVED</span>
        <h1>다시 보고 싶은 정책을<br />한곳에 모았습니다.</h1>
        <p>저장한 정책의 신청 기간과 지원 내용을 다시 확인하고, 필요 없어진 정책은 바로 정리할 수 있습니다.</p>
      </header>

      {error && <div className="error-banner">{error}</div>}
      {result && !error && (
        <div className="list-heading">
          <strong>{result.totalElements.toLocaleString("ko-KR")}개 저장</strong>
          <span>{result.totalPages > 0 ? `${result.page + 1} / ${result.totalPages} 페이지` : "저장한 정책 없음"}</span>
        </div>
      )}

      {loading && !result ? (
        <div className="empty-state"><h2>저장한 정책을 불러오고 있습니다.</h2><p>잠시만 기다려 주세요.</p></div>
      ) : result?.content.length ? (
        <div className="catalog-grid bookmark-grid" data-pending={loading ? "" : undefined}>
          {result.content.map((policy) => (
            <article className="catalog-card bookmark-card" key={policy.id}>
              <Link className="bookmark-card-main" href={`/policies/${policy.id}`}>
                <div className="catalog-card-topline">
                  <div className="catalog-meta"><span>{policy.category}</span><span>{regionLabel(policy.regionCode)}</span></div>
                </div>
                <h2>{policy.name}</h2>
                <p className="catalog-description">{policy.description}</p>
                <div className="catalog-highlights">
                  <div><span>지원 혜택</span><p>{summarizePolicyText(policy.supportContent)}</p></div>
                  <div><span>신청 기간</span><p>{applicationPeriod(policy)}</p></div>
                </div>
                <div className="catalog-footer"><span>{policy.provider}</span><strong>자세히 보기</strong></div>
              </Link>
              <div className="bookmark-card-actions">
                <button disabled={removingId === policy.id} type="button" onClick={() => removeBookmark(policy.id)}>
                  {removingId === policy.id ? "정리 중…" : "저장 해제"}
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : !error ? (
        <div className="empty-state">
          <h2>아직 저장한 정책이 없습니다.</h2>
          <p>관심 있는 정책을 저장하면 이곳에서 다시 확인할 수 있습니다.</p>
          <Link className="primary-link" href="/policies">정책 찾아보기</Link>
        </div>
      ) : null}

      {result && result.totalPages > 1 && (
        <nav className="pagination" aria-label="저장한 정책 페이지">
          <button className="pagination-direction" disabled={result.first || loading} type="button" onClick={() => setPage((current) => Math.max(current - 1, 1))}>이전</button>
          <div className="pagination-pages">
            {paginationItems(result.page + 1, result.totalPages).map((item) => typeof item === "number" ? (
              <button
                aria-current={item === result.page + 1 ? "page" : undefined}
                aria-label={`${item}페이지로 이동`}
                className={`pagination-page${item === result.page + 1 ? " current" : ""}`}
                disabled={loading}
                key={item}
                type="button"
                onClick={() => setPage(item)}
              >{item}</button>
            ) : <span className="pagination-ellipsis" aria-hidden="true" key={item}>…</span>)}
          </div>
          <button className="pagination-direction" disabled={result.last || loading} type="button" onClick={() => setPage((current) => current + 1)}>다음</button>
        </nav>
      )}
    </main>
  );
}
