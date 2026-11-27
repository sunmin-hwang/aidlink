"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiFetch, ApiError, clientApiBaseUrl, parseApiResponse } from "../../lib/api";
import { ApplicationPeriodDisplay } from "../../components/ApplicationPeriodDisplay";
import { BriefingEmailControls } from "../../components/BriefingEmailControls";
import type { Policy } from "../../lib/types";

type Summary = { id: string; status: "COMPLETED" | "FAILED"; createdAt: string };
type Item = Pick<Policy, "applicationStart" | "applicationEnd" | "applicationPeriod" | "sourceUrl" | "applicationUrl">
  & { policyId: number; name: string; provider: string; category: string; status: string; reason: string; checkConditions: string[] };
type Report = Summary & { failureMessage: string | null; content: null | {
  evaluationDate: string; regionCode: string | null; evaluatedCount: number;
  candidateLimitReached: boolean; guidance: string; items: Item[];
} };
const statusLabel: Record<string, string> = { ELIGIBLE: "조건 충족 가능성", NEAR_MISS: "부족 조건 확인", UNKNOWN: "추가 확인 필요" };
const dateLabel = (value: string) => new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });

export default function BriefingsPage() {
  const [access, setAccess] = useState<boolean | null>(null);
  const [history, setHistory] = useState<Summary[]>([]);
  const [report, setReport] = useState<Report | null>(null);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sequence = useRef(0);

  function showError(caught: unknown) {
    if (caught instanceof ApiError && (caught.status === 403 || caught.status === 401)) {
      setAccess(false); setReport(null); setHistory([]);
    }
    setError(caught instanceof Error ? caught.message : "브리핑을 불러오지 못했습니다. 다시 시도해 주세요.");
  }

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const result = await apiFetch<{ enabled: boolean }>("/api/v1/me/briefing-access", { cache: "no-store" });
        if (!active) return;
        setAccess(result.enabled);
        if (result.enabled) {
          const rows = await apiFetch<Summary[]>("/api/v1/me/briefings", { cache: "no-store" });
          if (active) setHistory(rows);
        }
      } catch (caught) { if (active) showError(caught); }
    }
    void load();
    return () => { active = false; sequence.current++; };
  }, []);

  async function openReport(id: string) {
    const requestId = ++sequence.current;
    setBusy(true); setError(null); setReport(null);
    try {
      const result = await apiFetch<Report>(`/api/v1/me/briefings/${id}`, { cache: "no-store" });
      if (requestId === sequence.current) setReport(result);
    } catch (caught) { if (requestId === sequence.current) showError(caught); }
    finally { if (requestId === sequence.current) setBusy(false); }
  }

  async function generate() {
    const requestId = ++sequence.current;
    setBusy(true); setError(null); setReport(null);
    try {
      const result = await apiFetch<Report>("/api/v1/me/briefings", { method: "POST" });
      if (requestId !== sequence.current) return;
      setReport(result);
      setHistory(rows => [result, ...rows].slice(0, 20));
    } catch (caught) { if (requestId === sequence.current) showError(caught); }
    finally { if (requestId === sequence.current) setBusy(false); }
  }

  async function downloadPdf() {
    if (!report || report.status !== "COMPLETED") return;
    setDownloading(true); setError(null);
    try {
      const response = await fetch(`${clientApiBaseUrl}/api/v1/me/briefings/${report.id}/pdf`, { credentials: "include", cache: "no-store" });
      if (!response.ok) await parseApiResponse(response);
      const url = URL.createObjectURL(await response.blob());
      const anchor = document.createElement("a");
      anchor.href = url; anchor.download = `aidlink-briefing-${report.id}.pdf`;
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (caught) { showError(caught); }
    finally { setDownloading(false); }
  }

  return <main className="page-shell shell">
    <header className="page-heading">
      <span className="eyebrow">05 — BRIEFING</span>
      <h1>내 조건으로 정리한<br />정책 브리핑</h1>
      <p>저장한 프로필로 추천 이유와 확인할 조건을 정리합니다. 생성 당시의 결과를 다시 확인할 수 있습니다.</p>
    </header>
    {error && <div className="error-banner" role="alert">{error}</div>}
    <div aria-live="polite">{access === null && !error ? <p>구독 권한을 확인하고 있습니다.</p> : null}</div>
    {access === false && <section className="empty-state">
      <h2>맞춤 브리핑은 구독 기능입니다.</h2>
      <p>현재 테스트 구독 권한을 부여받은 계정에서 이용할 수 있습니다. 실제 결제는 제공하지 않습니다.</p>
      <Link href="/policies">무료 정책 검색 이용하기</Link>
    </section>}
    {access === true && <>
      <section className="briefing-controls" aria-label="브리핑 생성">
        <p>현재 테스트 구독 권한으로 이용 중입니다.</p>
        <div><button type="button" disabled={busy} onClick={generate}>{busy ? "처리 중…" : "새 브리핑 생성"}</button> <Link href="/profile">프로필 확인·수정</Link></div>
        <p className="muted">완료된 브리핑은 PDF로 다운로드하거나, 수신 주소 확인 후 이메일로 보낼 수 있습니다.</p>
      </section>
      <div className="briefing-layout">
        <aside className="briefing-history" aria-label="최근 브리핑">
          <h2>최근 브리핑</h2>
          {history.length === 0 && <p>생성한 브리핑이 없습니다.</p>}
          <ul>{history.map(row => <li key={row.id}>
            <button type="button" disabled={busy} aria-pressed={report?.id === row.id} onClick={() => openReport(row.id)}>
              {dateLabel(row.createdAt)} · {row.status === "FAILED" ? "생성 실패" : "보기"}
            </button>
          </li>)}</ul>
          <p className="muted">최근 20건 · 한국시간</p>
        </aside>
        <section className="briefing-report" aria-label="브리핑 결과" aria-busy={busy}>
          <div aria-live="polite">{busy ? "브리핑을 처리하고 있습니다." : null}</div>
          {report?.status === "FAILED" && <div role="alert"><h2>생성하지 못했습니다.</h2><p>{report.failureMessage}</p><p>잠시 후 새 브리핑을 생성해 주세요.</p></div>}
          {report?.content && <>
            <h2>{report.content.evaluationDate} 맞춤 브리핑</h2>
            <button type="button" disabled={downloading || busy} onClick={downloadPdf}>{downloading ? "PDF 준비 중…" : "PDF 다운로드"}</button>
            <p>{report.content.guidance}</p>
            <p>평가한 정책 {report.content.evaluatedCount}개 · 담은 정책 {report.content.items.length}개</p>
            {report.content.candidateLimitReached && <p>조건 미구조화 정책은 일부만 검토했습니다. 무료 검색에서 더 많은 정책을 확인할 수 있습니다.</p>}
            {report.content.items.length === 0 && <div className="empty-state"><h3>이번 브리핑에 담을 정책이 없습니다.</h3><p>프로필을 확인하거나 무료 정책 검색을 이용해 주세요.</p></div>}
            {report.content.items.map(item => <article className="briefing-item" key={item.policyId}>
              <div className="catalog-meta"><span>{item.category}</span><span>{statusLabel[item.status] ?? "확인 필요"}</span></div>
              <h3><Link href={`/policies/${item.policyId}`}>{item.name}</Link></h3>
              <p>{item.provider}</p><p>{item.reason}</p>
              <ul>{item.checkConditions.map((condition, index) => <li key={`${index}-${condition}`}>{condition}</li>)}</ul>
              <p>신청 기간: <ApplicationPeriodDisplay policy={item} /></p>
              <div className="briefing-links">
                {item.sourceUrl ? <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">공식 원문 ↗</a> : <span>공식 원문 링크 확인 필요</span>}
                {item.applicationUrl && <a href={item.applicationUrl} target="_blank" rel="noopener noreferrer">신청 안내 ↗</a>}
              </div>
            </article>)}
          </>}
          {!report && !busy && <p>새 브리핑을 생성하거나 최근 브리핑을 선택해 주세요.</p>}
        </section>
      </div>
      <BriefingEmailControls key={report?.id ?? "no-report"} reportId={report?.status === "COMPLETED" ? report.id : null} />
    </>}
  </main>;
}
