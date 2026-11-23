"use client";

import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, ApiError } from "../lib/api";

const statusLabels: Record<string, string> = {
  UNCHECKED: "미점검", CONTENT_MATCH: "본문 일치 후보", OTHER_PAGE: "본문 확인 필요",
  ERROR_PAGE: "오류 페이지", MAINTENANCE: "점검·이용 불가", CONNECTION_FAILED: "연결 실패", BLOCKED_URL: "게시 제외 주소",
};
type PolicyLink = {
  policyId: number; policyName: string; provider: string; sourceUrl: string;
  verifiedSourceUrl: string | null; verifiedAt: string | null; checkedUrl: string | null;
  status: string; checkedAt: string | null; finalUrl: string | null; httpStatus: number | null;
  reason: string | null; failures: number; nextCheckAt: string | null;
  reviewedBy: string | null; reviewEvidence: string | null;
};
type LinkPage = { content: PolicyLink[]; totalElements: number; totalPages: number; page: number; size: number };

export default function AdminPolicyLinks() {
  const [criteria, setCriteria] = useState({ page: 0, status: "", q: "", revision: 0 });
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [data, setData] = useState<LinkPage | null>(null);
  const [selected, setSelected] = useState<PolicyLink | null>(null);
  const [url, setUrl] = useState("");
  const [evidence, setEvidence] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams({ page: String(criteria.page), status: criteria.status, q: criteria.q });
    apiFetch<LinkPage>(`/api/v1/admin/policies/links?${params}`, { signal: controller.signal })
      .then(setData).catch((caught) => {
        if (!controller.signal.aborted) setError(messageOf(caught));
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [criteria]);

  function choose(policy: PolicyLink) {
    setSelected(policy);
    setUrl(policy.checkedUrl || policy.verifiedSourceUrl || "");
    setEvidence(""); setConfirmed(false); setMessage(""); setError("");
  }

  function refresh(page = criteria.page) {
    setLoading(true);
    setCriteria((previous) => ({ ...previous, page, revision: previous.revision + 1 }));
  }

  async function act(action: "check" | "approve") {
    if (!selected) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const result = await apiFetch<PolicyLink>(`/api/v1/admin/policies/links/${selected.policyId}/${action}`, {
        method: action === "check" ? "POST" : "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, ...(action === "approve" ? { evidence } : {}) }),
      });
      setSelected(result); setConfirmed(false);
      if (action === "check") setUrl(result.checkedUrl || "");
      setMessage(action === "check" ? "점검을 완료했습니다. 본문 일치 후보도 직접 내용을 확인한 뒤 등록해 주세요." : "검수 근거와 함께 게시용 원문을 등록했습니다.");
      refresh();
    } catch (caught) { setError(messageOf(caught)); }
    finally { setBusy(false); }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLoading(true); setError("");
    setCriteria((previous) => ({ page: 0, status, q: query.trim(), revision: previous.revision + 1 }));
  }

  return (
    <section className="admin-section source-link-admin" aria-labelledby="source-link-title" aria-busy={busy || loading}>
      <div className="admin-section-heading"><div><h2 id="source-link-title">공식 원문 점검</h2><p>자동 점검은 본문 후보를 찾습니다. 제공기관·지원 대상·신청 절차를 직접 확인한 주소만 게시해 주세요.</p></div></div>
      <form className="lifecycle-toolbar" onSubmit={search}>
        <label>정책명·식별자<input value={query} onChange={(event) => setQuery(event.target.value)} /></label>
        <label>점검 상태<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">전체</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <button type="submit" disabled={busy || loading}>검색</button>
      </form>
      {error && <p className="error-banner" role="alert">{error}</p>}
      <p role="status" aria-live="polite">{busy ? "원문을 확인하고 있습니다…" : loading ? "목록을 불러오고 있습니다…" : message}</p>
      <div className="lifecycle-admin-layout">
        <aside className="lifecycle-policy-list">
          <p>{data ? `총 ${data.totalElements.toLocaleString("ko-KR")}건 · ${data.totalPages ? data.page + 1 : 0}/${data.totalPages}페이지` : "점검 목록"}</p>
          {!loading && data?.content.length === 0 && <p>조건에 맞는 정책이 없습니다.</p>}
          {data?.content.map((policy) => <button type="button" key={policy.policyId} disabled={busy || loading} className={selected?.policyId === policy.policyId ? "selected" : ""} onClick={() => choose(policy)}><strong>{policy.policyName}</strong><span>{policy.provider} · {statusLabels[policy.status] || "확인 필요"}</span></button>)}
          {data && data.totalPages > 1 && <nav className="admin-pagination" aria-label="원문 점검 목록 페이지"><button type="button" disabled={busy || loading || data.page === 0} onClick={() => refresh(data.page - 1)}>이전</button><span>{data.page + 1} / {data.totalPages}</span><button type="button" disabled={busy || loading || data.page + 1 >= data.totalPages} onClick={() => refresh(data.page + 1)}>다음</button></nav>}
        </aside>
        <div className="deletion-impact-panel source-link-editor">
          {!selected ? <p>정책을 선택해 제공기관의 원문 후보 주소를 점검해 주세요.</p> : <>
            <h3>{selected.policyName}</h3><p>{selected.provider}</p>
            <dl><dt>수집 원본</dt><dd>{selected.sourceUrl || "없음"}</dd><dt>게시용 검수 주소</dt><dd>{selected.verifiedSourceUrl || "미등록"}</dd><dt>최근 점검</dt><dd>{statusLabels[selected.status] || "확인 필요"} · {dateOf(selected.checkedAt)} · HTTP {selected.httpStatus ?? "미확인"}</dd><dt>최종 이동 주소</dt><dd>{selected.finalUrl || "미확인"}</dd><dt>판정 사유</dt><dd>{selected.reason || "아직 점검하지 않았습니다."}</dd><dt>연속 실패·다음 점검</dt><dd>{selected.failures}회 · {dateOf(selected.nextCheckAt)}</dd><dt>마지막 검수</dt><dd>{dateOf(selected.verifiedAt)} · {selected.reviewedBy || "기존 검수 기록"}</dd><dt>검수 근거</dt><dd>{selected.reviewEvidence || "미기록"}</dd></dl>
            <label>제공기관 원문 후보<input type="url" maxLength={4000} value={url} disabled={busy} onChange={(event) => { setUrl(event.target.value); setConfirmed(false); }} placeholder="https://제공기관/정책본문" /></label>
            <p>공공·비영리·교육기관의 국내 주소를 점검합니다. 주소를 비우면 기존 게시용 주소 또는 수집 원본을 확인합니다.</p>
            <button type="button" className="primary" disabled={busy || loading} onClick={() => act("check")}>주소 점검</button>
            {/^https?:\/\//i.test(url) && selected.status !== "BLOCKED_URL" && selected.checkedUrl === url && <a href={url} target="_blank" rel="noopener noreferrer">후보 본문 직접 열기 ↗</a>}
            <label>검수 근거<textarea value={evidence} minLength={20} maxLength={4000} rows={4} disabled={busy} onChange={(event) => setEvidence(event.target.value)} placeholder="제공기관, 정책명, 지원 대상과 신청 절차가 일치하는 근거를 남겨 주세요." /></label>
            <label className="source-link-confirm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={(event) => setConfirmed(event.target.checked)} />제공기관과 정책 본문을 직접 확인했습니다.</label>
            <button type="button" className="primary" disabled={busy || loading || !confirmed || evidence.trim().length < 20 || selected.status !== "CONTENT_MATCH" || selected.checkedUrl !== url} onClick={() => act("approve")}>검수 완료 및 원문 등록</button>
          </>}
        </div>
      </div>
    </section>
  );
}

function dateOf(value: string | null) { return value ? new Date(value).toLocaleString("ko-KR") : "미기록"; }
function messageOf(value: unknown) { return value instanceof ApiError ? value.message : "원문 점검 요청을 처리하지 못했습니다. 다시 시도해 주세요."; }
