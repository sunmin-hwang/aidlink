"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, ApiError } from "../lib/api";
import { applicationPeriod } from "../lib/policy-display";
import type { Policy } from "../lib/types";

type PeriodView = { policyId: number; policyName: string; lifecycleStatus: Policy["lifecycleStatus"]; start: string | null; end: string | null;
  period: NonNullable<Policy["applicationPeriod"]>; verifiedText: string | null; verifiedSourceUrl: string | null; reviewer: string | null; evidence: string | null };
type SearchPage = { content: { id: number; name: string; provider: string; lifecycle_status: string }[]; totalElements: number; page: number; totalPages: number };
type Preview = { fingerprint: string; total: number; sourceKinds: Record<string, number>; expired: number; verifiedPreserved: number; dryRun: boolean };

export default function AdminPolicyPeriods() {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState<SearchPage | null>(null);
  const [selected, setSelected] = useState<PeriodView | null>(null);
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [evidence, setEvidence] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function run(action: () => Promise<void>) {
    setBusy(true); setError(""); setMessage("");
    try { await action(); } catch (caught) { setError(caught instanceof ApiError ? caught.message : "신청기간 요청을 처리하지 못했습니다."); }
    finally { setBusy(false); }
  }
  async function search(index = 0) {
    const params = new URLSearchParams({ q: query, page: String(index) });
    setPage(await apiFetch<SearchPage>(`/api/v1/admin/policies/periods?${params}`));
  }
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void run(() => search()); }
  async function choose(id: number) {
    const result = await apiFetch<PeriodView>(`/api/v1/admin/policies/periods/${id}`);
    setSelected(result); setText(result.verifiedText || result.period.sourceText); setUrl(result.verifiedSourceUrl || "");
    setEvidence(""); setConfirmed(false);
  }
  async function save() {
    if (!selected) return;
    const result = await apiFetch<PeriodView>(`/api/v1/admin/policies/periods/${selected.policyId}/verify`, {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ periodText: text, sourceUrl: url, evidence, expectedSourceText: selected.period.sourceText }),
    });
    setSelected(result); setConfirmed(false); setPreview(null);
    setMessage("제공기관에서 확인한 신청기간을 저장했습니다. 수집 원문은 보존됩니다.");
  }
  async function dryRun() { setPreview(await apiFetch<Preview>("/api/v1/admin/policies/periods/backfill/preview")); }
  async function apply() {
    if (!preview) return;
    const result = await apiFetch<Preview>("/api/v1/admin/policies/periods/backfill/apply", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ fingerprint: preview.fingerprint }),
    });
    setPreview(null); setSelected(null); setMessage(`${result.total.toLocaleString("ko-KR")}건의 신청기한 분류를 반영했습니다. 검수 기한은 보존했습니다.`);
  }
  return <section className="admin-section" aria-labelledby="application-period-title" aria-busy={busy}>
    <div className="admin-section-heading"><div><h2 id="application-period-title">신청기간 검수</h2><p>수집 기한을 확인하고, 제공기관에서 공고한 연장 기한을 근거와 함께 등록합니다. 연도와 날짜가 명확한 신청기간만 입력해 주세요.</p></div></div>
    <div className="source-link-editor deletion-impact-panel">
      <h3>기존 정책 신청기한 구조화</h3><p>모호한 일정은 기간 미상으로 남기며, 만료된 정책은 검색·분석에서 제외됩니다. 먼저 전체 결과를 미리 확인해 주세요.</p>
      <button type="button" disabled={busy} onClick={() => run(dryRun)}>전체 기한 미리보기</button>
      {preview && <><p>전체 {preview.total.toLocaleString("ko-KR")}건 · 날짜 확인 {preview.sourceKinds.FIXED || 0}건 · 상시 {preview.sourceKinds.YEAR_ROUND || 0}건 · 기간 미상 {preview.sourceKinds.UNKNOWN || 0}건 · 만료 판정 {preview.expired}건 · 검수 기한 보존 {preview.verifiedPreserved}건</p>
        <button type="button" disabled={busy} onClick={() => run(apply)}>미리보기 결과 반영</button></>}
    </div>
    <form className="lifecycle-toolbar" onSubmit={submit}><label>신청기간 검수 정책 검색<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="정책명·식별자·번호" /></label><button disabled={busy}>조회</button></form>
    {error && <p className="error-banner" role="alert">{error}</p>}
    <p role="status" aria-live="polite">{busy ? "신청기간 요청을 처리하고 있습니다…" : message}</p>
    <div className="lifecycle-admin-layout"><aside className="lifecycle-policy-list">
      {page && <p>전체 {page.totalElements.toLocaleString("ko-KR")}건 · {page.totalPages ? page.page + 1 : 0}/{page.totalPages}페이지</p>}
      {page?.content.map((policy) => <button key={policy.id} type="button" disabled={busy} className={selected?.policyId === policy.id ? "selected" : ""} onClick={() => run(() => choose(policy.id))}><strong>{policy.name}</strong><span>{policy.provider} · #{policy.id}</span></button>)}
      {page?.content.length === 0 && <p>조건에 맞는 정책이 없습니다.</p>}
      {page && page.totalPages > 1 && <nav className="admin-pagination" aria-label="신청기간 검수 페이지"><button disabled={busy || page.page === 0} onClick={() => run(() => search(page.page - 1))}>이전</button><span>{page.page + 1}/{page.totalPages}</span><button disabled={busy || page.page + 1 >= page.totalPages} onClick={() => run(() => search(page.page + 1))}>다음</button></nav>}
    </aside><div className="source-link-editor deletion-impact-panel">
      {!selected ? <p>조회한 정책을 선택해 수집 기한과 검수 기한을 비교해 주세요.</p> : <>
        <h3>{selected.policyName}</h3><dl><dt>유효 신청기간</dt><dd>{applicationPeriod({ applicationStart: selected.start, applicationEnd: selected.end, applicationPeriod: selected.period, lifecycleStatus: selected.lifecycleStatus })}</dd>
          <dt>수집 원문 기한</dt><dd>{selected.period.sourceText || "정보 없음"}</dd><dt>이전 검수 근거</dt><dd>{selected.evidence || "미등록"}</dd></dl>
        <label>확인한 신청기간<input disabled={busy} value={text} maxLength={2000} onChange={(event) => { setText(event.target.value); setConfirmed(false); }} placeholder="2026년 10월 7일(수) 10:00 ~ 2026년 10월 20일(화)" /></label>
        <label>기간 확인 공식 원문<input type="url" value={url} disabled={busy} onChange={(event) => { setUrl(event.target.value); setConfirmed(false); }} /></label>
        {selected.verifiedSourceUrl && /^https?:\/\//i.test(selected.verifiedSourceUrl) && <a href={selected.verifiedSourceUrl} target="_blank" rel="noopener noreferrer">등록된 제공기관 원문 열기 ↗</a>}
        <label>신청기간 검수 근거<textarea value={evidence} disabled={busy} minLength={20} maxLength={4000} rows={4} onChange={(event) => setEvidence(event.target.value)} placeholder="공고의 신청기간·연장 안내와 확인한 내용을 남겨 주세요." /></label>
        <label className="source-link-confirm"><input type="checkbox" disabled={busy} checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />공식 원문의 신청기간을 직접 확인했습니다.</label>
        <button type="button" className="primary" disabled={busy || !confirmed || !text || !url || evidence.trim().length < 20} onClick={() => run(save)}>검수 신청기간 저장</button>
      </>}
    </div></div>
  </section>;
}
