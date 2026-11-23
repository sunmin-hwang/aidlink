"use client";

import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, ApiError } from "../../lib/api";
import { paginationItems } from "../../lib/pagination";
import AdminPolicyLinks from "../../components/AdminPolicyLinks";
import AdminPolicyPeriods from "../../components/AdminPolicyPeriods";

type Review = {
  id: number;
  policyId: number;
  policyName: string;
  reason: string;
  priorityScore: number;
  status: string;
  createdAt: string;
};

type ReviewPage = {
  content: Review[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
};

type Analysis = {
  policyId: number;
  policyName: string;
  analysisStatus: string;
  latestCandidate: { candidate: { rule: unknown; ambiguities: string[]; model: string } } | null;
  latestValidation: { status: string; score: number; issues: unknown[]; rule: unknown } | null;
};

type LifecycleStatus = "ACTIVE" | "EXPIRED" | "SOURCE_REMOVED" | "ARCHIVED";

type LifecyclePolicy = {
  policyId: number;
  externalId: string;
  policyName: string;
  provider: string;
  category: string;
  lifecycleStatus: LifecycleStatus;
  applicationEnd: string | null;
  lastSeenAt: string | null;
  missedCollectionCount: number;
  archivedAt: string | null;
};

type LifecyclePage = {
  content: LifecyclePolicy[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  status: LifecycleStatus | null;
  query: string | null;
  first: boolean;
  last: boolean;
};

type ReferenceCounts = {
  policySources: number;
  policyRules: number;
  policyRuleVersions: number;
  policyRuleCandidates: number;
  policyRuleValidations: number;
  simulationResults: number;
  bookmarks: number;
  adminReviews: number;
  activeAdminReviews: number;
  subscriptionMatches: number;
  rawPolicySnapshots: number;
};

type DeletionImpact = {
  policyId: number;
  policyName: string;
  lifecycleStatus: LifecycleStatus;
  dryRun: boolean;
  minimumRetentionDays: number;
  archivedAt: string | null;
  retentionUntil: string | null;
  references: ReferenceCounts;
  cascadingRows: number;
  preservedRawPolicyRows: number;
  safeToDelete: boolean;
  blockers: string[];
  previewedAt: string;
};

const lifecycleStatusLabels: Record<LifecycleStatus, string> = {
  ACTIVE: "활성",
  EXPIRED: "신청 종료",
  SOURCE_REMOVED: "공식 소스 미확인",
  ARCHIVED: "보관",
};

const referenceLabels: Array<[keyof ReferenceCounts, string]> = [
  ["bookmarks", "사용자 북마크"],
  ["simulationResults", "시뮬레이션 결과"],
  ["subscriptionMatches", "구독 정책 매칭"],
  ["adminReviews", "관리자 검수 이력"],
  ["activeAdminReviews", "진행 중 검수"],
  ["policySources", "정책 출처 연결"],
  ["policyRules", "현재 Rule"],
  ["policyRuleVersions", "Rule 버전"],
  ["policyRuleCandidates", "AI Rule 후보"],
  ["policyRuleValidations", "AI Rule 검증"],
  ["rawPolicySnapshots", "보존 원본"],
];

export default function AdminPage() {
  const [reviewPage, setReviewPage] = useState<ReviewPage | null>(null);
  const [reviewPageNumber, setReviewPageNumber] = useState(0);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [policyId, setPolicyId] = useState<number | null>(null);
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [ruleText, setRuleText] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [lifecyclePage, setLifecyclePage] = useState<LifecyclePage | null>(null);
  const [lifecycleStatus, setLifecycleStatus] = useState<"" | Exclude<LifecycleStatus, "ACTIVE">>("ARCHIVED");
  const [lifecycleQuery, setLifecycleQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [lifecyclePageNumber, setLifecyclePageNumber] = useState(0);
  const [selectedLifecycleId, setSelectedLifecycleId] = useState<number | null>(null);
  const [impact, setImpact] = useState<DeletionImpact | null>(null);
  const [lifecycleBusy, setLifecycleBusy] = useState<string | null>(null);
  const [lifecycleMessage, setLifecycleMessage] = useState<string | null>(null);

  const loadReviews = useCallback(async (page: number) => {
    setReviewLoading(true);
    const result = await apiFetch<ReviewPage>(`/api/v1/admin/reviews?page=${page}&size=30`);
    setReviewPage(result);
    setReviewPageNumber(result.page);
    setPolicyId((current) => result.content.some((review) => review.policyId === current)
      ? current
      : result.content[0]?.policyId ?? null);
    setReviewLoading(false);
  }, []);

  const loadAnalysis = useCallback(async (id: number) => {
    const result = await apiFetch<Analysis>(`/api/v1/admin/policies/${id}/analysis`);
    setAnalysis(result);
    const rule = result.latestValidation?.rule ?? result.latestCandidate?.candidate.rule;
    setRuleText(rule ? JSON.stringify(rule, null, 2) : "");
  }, []);

  const loadLifecycle = useCallback(async (
    page: number,
    status: "" | Exclude<LifecycleStatus, "ACTIVE">,
    query: string,
  ) => {
    const params = new URLSearchParams({ page: String(page), size: "20" });
    if (status) params.set("status", status);
    if (query) params.set("q", query);
    const result = await apiFetch<LifecyclePage>(`/api/v1/admin/policies/lifecycle?${params}`);
    setLifecyclePage(result);
    setLifecyclePageNumber(result.page);
    setSelectedLifecycleId((current) => result.content.some((policy) => policy.policyId === current)
      ? current
      : result.content[0]?.policyId ?? null);
  }, []);

  useEffect(() => {
    loadReviews(reviewPageNumber).catch((caught) => {
      setReviewLoading(false);
      setError(messageOf(caught));
    });
  }, [loadReviews, reviewPageNumber]);

  useEffect(() => {
    if (policyId !== null) loadAnalysis(policyId).catch((caught) => setError(messageOf(caught)));
  }, [loadAnalysis, policyId]);

  useEffect(() => {
    loadLifecycle(lifecyclePageNumber, lifecycleStatus, appliedQuery)
      .catch((caught) => setError(messageOf(caught)));
  }, [appliedQuery, lifecyclePageNumber, lifecycleStatus, loadLifecycle]);

  useEffect(() => {
    if (selectedLifecycleId === null) {
      setImpact(null);
      return;
    }
    setImpact(null);
    apiFetch<DeletionImpact>(`/api/v1/admin/policies/${selectedLifecycleId}/deletion-impact`)
      .then(setImpact)
      .catch((caught) => setError(messageOf(caught)));
  }, [selectedLifecycleId]);

  async function runAction(name: string, action: () => Promise<unknown>) {
    setBusy(name);
    setError(null);
    try {
      await action();
      if (policyId !== null) await loadAnalysis(policyId);
      await loadReviews(reviewPageNumber);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(null);
    }
  }

  async function saveRule() {
    if (policyId === null) return;
    let rule: unknown;
    try {
      rule = JSON.parse(ruleText);
    } catch {
      setError("Rule JSON 문법을 확인해주세요.");
      return;
    }
    await runAction("save", () => apiFetch<void>(`/api/v1/admin/policies/${policyId}/rule`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rule }),
    }));
  }

  function submitLifecycleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLifecycleMessage(null);
    setLifecyclePageNumber(0);
    setAppliedQuery(lifecycleQuery.trim());
  }

  async function restorePolicy() {
    if (selectedLifecycleId === null) return;
    setLifecycleBusy("restore");
    setLifecycleMessage(null);
    setError(null);
    try {
      const restored = await apiFetch<LifecyclePolicy>(`/api/v1/admin/policies/${selectedLifecycleId}/restore`, { method: "POST" });
      setLifecycleMessage(`${restored.policyName} 정책을 ${lifecycleStatusLabels[restored.lifecycleStatus]} 상태로 복구했습니다.`);
      setSelectedLifecycleId(null);
      setImpact(null);
      await loadLifecycle(lifecyclePageNumber, lifecycleStatus, appliedQuery);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setLifecycleBusy(null);
    }
  }

  return (
    <main className="page-shell shell">
      <header className="page-heading">
        <span className="eyebrow">HUMAN-IN-THE-LOOP</span>
        <h1>정책 운영 관리</h1>
        <p>Rule 검수와 보관 정책 복구, 삭제 전 참조 영향을 한 화면에서 안전하게 확인합니다.</p>
      </header>
      {error && <p className="error-banner">{error}</p>}

      <section className="admin-section" aria-labelledby="rule-review-title">
        <div className="admin-section-heading">
          <div><span>01 — RULE REVIEW</span><h2 id="rule-review-title">정책 검수 Queue</h2></div>
          <p>자동 검증에서 위험 신호가 발견된 정책만 확인합니다.</p>
        </div>
        <div className="admin-layout">
          <aside className="review-list">
            <strong>대기 {reviewPage?.totalElements.toLocaleString("ko-KR") ?? 0}건</strong>
            {reviewPage?.content.length === 0 && <p>현재 검수 대기 항목이 없습니다.</p>}
            {reviewPage?.content.map((review) => (
              <button className={policyId === review.policyId ? "selected" : ""} key={review.id} type="button" onClick={() => setPolicyId(review.policyId)}>
                <span>{review.reason}</span>
                <b>{review.policyName}</b>
                <small>우선순위 {review.priorityScore} · {review.status}</small>
              </button>
            ))}
            {reviewPage && reviewPage.totalPages > 1 && (
              <nav className="admin-pagination review-pagination" aria-label="정책 검수 Queue 페이지">
                <button disabled={reviewPage.first || reviewLoading} type="button" onClick={() => setReviewPageNumber(reviewPage.page - 1)}>이전</button>
                <div>
                  {paginationItems(reviewPage.page + 1, reviewPage.totalPages).map((item) => typeof item === "number" ? (
                    <button className={item === reviewPage.page + 1 ? "current" : ""} disabled={item === reviewPage.page + 1 || reviewLoading} key={item} type="button" onClick={() => setReviewPageNumber(item - 1)}>{item}</button>
                  ) : <span key={item}>…</span>)}
                </div>
                <button disabled={reviewPage.last || reviewLoading} type="button" onClick={() => setReviewPageNumber(reviewPage.page + 1)}>다음</button>
              </nav>
            )}
          </aside>
          <section className="admin-workbench">
            {!analysis ? <p>검수할 정책을 선택해주세요.</p> : (
              <>
                <div className="admin-heading">
                  <div><span>Policy #{analysis.policyId}</span><h2>{analysis.policyName}</h2></div>
                  <b>{analysis.analysisStatus}</b>
                </div>
                <div className="admin-actions">
                  <button disabled={busy !== null} onClick={() => runAction("parse", () => apiFetch(`/api/v1/admin/policies/${analysis.policyId}/parse`, { method: "POST" }))}>AI 후보 생성</button>
                  <button disabled={busy !== null || !analysis.latestCandidate} onClick={() => runAction("validate", () => apiFetch(`/api/v1/admin/policies/${analysis.policyId}/validate`, { method: "POST" }))}>자동 검증</button>
                  <button disabled={busy !== null || !ruleText} onClick={saveRule}>Rule 초안 저장</button>
                  <button className="verify" disabled={busy !== null || !ruleText} onClick={() => runAction("verify", () => apiFetch(`/api/v1/admin/policies/${analysis.policyId}/verify`, { method: "POST" }))}>관리자 검증 완료</button>
                </div>
                <div className="validation-summary">
                  <span>자동 검증</span>
                  <strong>{analysis.latestValidation?.status ?? "미실행"}</strong>
                  <small>{analysis.latestValidation ? `${analysis.latestValidation.score}점 · 이슈 ${analysis.latestValidation.issues.length}개` : "후보 생성 후 검증할 수 있습니다."}</small>
                </div>
                <label className="rule-editor">구조화 Rule JSON<textarea spellCheck={false} value={ruleText} onChange={(event) => setRuleText(event.target.value)} placeholder="AI 후보가 생성되면 Rule JSON이 표시됩니다." /></label>
                {busy && <p className="saved-message">{busy} 처리 중…</p>}
              </>
            )}
          </section>
        </div>
      </section>

      <AdminPolicyLinks />
      <AdminPolicyPeriods />
      <section className="admin-section lifecycle-admin-section" aria-labelledby="lifecycle-title">
        <div className="admin-section-heading">
          <div><span>02 — LIFECYCLE</span><h2 id="lifecycle-title">보관 정책과 삭제 영향</h2></div>
          <p>복구는 즉시 반영되지만 영구 삭제는 제공하지 않습니다. dry-run 결과만 확인할 수 있습니다.</p>
        </div>

        <form className="lifecycle-toolbar" onSubmit={submitLifecycleSearch}>
          <label>상태<select value={lifecycleStatus} onChange={(event) => { setLifecycleStatus(event.target.value as typeof lifecycleStatus); setLifecyclePageNumber(0); setLifecycleMessage(null); }}>
            <option value="ARCHIVED">보관</option>
            <option value="SOURCE_REMOVED">공식 소스 미확인</option>
            <option value="EXPIRED">신청 종료</option>
            <option value="">전체 비활성</option>
          </select></label>
          <label>정책 검색<input value={lifecycleQuery} onChange={(event) => setLifecycleQuery(event.target.value)} placeholder="정책명·기관·외부 ID" /></label>
          <button type="submit">검색</button>
        </form>

        {lifecycleMessage && <p className="saved-message">{lifecycleMessage}</p>}
        <div className="lifecycle-admin-layout">
          <aside className="lifecycle-policy-list">
            <strong>{lifecyclePage?.totalElements.toLocaleString("ko-KR") ?? 0}건</strong>
            {lifecyclePage?.content.length === 0 && <p>조건에 맞는 비활성 정책이 없습니다.</p>}
            {lifecyclePage?.content.map((policy) => (
              <button className={selectedLifecycleId === policy.policyId ? "selected" : ""} key={policy.policyId} type="button" onClick={() => setSelectedLifecycleId(policy.policyId)}>
                <span>{lifecycleStatusLabels[policy.lifecycleStatus]}</span>
                <b>{policy.policyName}</b>
                <small>{policy.provider} · #{policy.policyId}</small>
              </button>
            ))}
            {lifecyclePage && lifecyclePage.totalPages > 1 && (
              <nav className="admin-pagination" aria-label="비활성 정책 페이지">
                <button disabled={lifecyclePage.first} type="button" onClick={() => setLifecyclePageNumber(lifecyclePage.page - 1)}>이전</button>
                <div>
                  {paginationItems(lifecyclePage.page + 1, lifecyclePage.totalPages).map((item) => typeof item === "number" ? (
                    <button className={item === lifecyclePage.page + 1 ? "current" : ""} disabled={item === lifecyclePage.page + 1} key={item} type="button" onClick={() => setLifecyclePageNumber(item - 1)}>{item}</button>
                  ) : <span key={item}>…</span>)}
                </div>
                <button disabled={lifecyclePage.last} type="button" onClick={() => setLifecyclePageNumber(lifecyclePage.page + 1)}>다음</button>
              </nav>
            )}
          </aside>

          <section className="deletion-impact-panel">
            {!impact ? <p>정책을 선택하면 복구 가능 상태와 삭제 영향을 계산합니다.</p> : (
              <>
                <div className="impact-heading">
                  <div><span>DRY RUN · Policy #{impact.policyId}</span><h3>{impact.policyName}</h3></div>
                  <b className={impact.safeToDelete ? "safe" : "blocked"}>{impact.safeToDelete ? "삭제 조건 충족" : "삭제 차단"}</b>
                </div>
                <p className="impact-retention">현재 {lifecycleStatusLabels[impact.lifecycleStatus]} · 최소 보관 {impact.minimumRetentionDays}일 {impact.retentionUntil && `· ${formatDateTime(impact.retentionUntil)} 이후 검토`}</p>
                <div className="impact-totals">
                  <article><span>연쇄 삭제 예상</span><strong>{impact.cascadingRows.toLocaleString("ko-KR")}건</strong></article>
                  <article><span>보존 원본</span><strong>{impact.preservedRawPolicyRows.toLocaleString("ko-KR")}건</strong></article>
                </div>
                <div className="reference-grid">
                  {referenceLabels.map(([key, label]) => <div key={key}><span>{label}</span><strong>{impact.references[key].toLocaleString("ko-KR")}</strong></div>)}
                </div>
                <div className="impact-blockers">
                  <strong>{impact.blockers.length > 0 ? `차단 사유 ${impact.blockers.length}건` : "차단 사유 없음"}</strong>
                  {impact.blockers.length > 0 ? <ul>{impact.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul> : <p>현재 기준으로 삭제 조건을 충족하지만, 별도 승인과 백업 없이는 삭제하지 않습니다.</p>}
                </div>
                {impact.lifecycleStatus === "EXPIRED" ? (
                  <p className="impact-expired-note">신청 기간이 끝난 정책은 복구해도 활성화되지 않으므로 복구 대상에서 제외합니다.</p>
                ) : (
                  <button className="restore-policy-button" disabled={lifecycleBusy !== null} type="button" onClick={restorePolicy}>{lifecycleBusy ? "복구 중…" : "정책 복구"}</button>
                )}
                <p className="impact-footnote">이 화면은 조회 전용 dry-run입니다. 영구 삭제 버튼과 실행 API는 제공하지 않습니다.</p>
              </>
            )}
          </section>
        </div>
      </section>
    </main>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function messageOf(caught: unknown) {
  return caught instanceof ApiError ? caught.message : "관리자 요청을 처리하지 못했습니다.";
}
