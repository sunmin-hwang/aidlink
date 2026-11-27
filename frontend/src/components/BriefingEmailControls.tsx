"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../lib/api";

type EmailStatus = { email: string; verified: boolean; available: boolean };
type Delivery = { id: string; briefingId: string; status: string; scheduledAt: string; sentAt: string | null; failureMessage: string | null };
const labels: Record<string, string> = { SCHEDULED: "예약됨", SENDING: "발송 중", SENT: "메일 서버 접수", FAILED: "준비 실패", UNKNOWN: "발송 결과 확인 필요", CANCELLED: "취소됨" };
const dateLabel = (value: string) => new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });

export function BriefingEmailControls({ reportId }: { reportId: string | null }) {
  const [email, setEmail] = useState<EmailStatus | null>(null);
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [token, setToken] = useState("");
  const [consent, setConsent] = useState(false);
  const [scheduledAt, setScheduledAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const request = useRef<{ signature: string; id: string } | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    const [status, history] = await Promise.all([
      apiFetch<EmailStatus>("/api/v1/me/briefing-email", { cache: "no-store", signal }),
      apiFetch<Delivery[]>("/api/v1/me/briefing-deliveries", { cache: "no-store", signal }),
    ]);
    if (!signal?.aborted) { setEmail(status); setDeliveries(history); }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal).catch(caught => { if (!controller.signal.aborted) setError(caught instanceof Error ? caught.message : "이메일 설정을 불러오지 못했습니다."); });
    return () => controller.abort();
  }, [load]);
  const waiting = deliveries.some(row => row.status === "SCHEDULED" || row.status === "SENDING");
  useEffect(() => {
    if (!waiting) return;
    const controller = new AbortController();
    const timer = setInterval(() => { void load(controller.signal).catch(() => {}); }, 10000);
    return () => { clearInterval(timer); controller.abort(); };
  }, [waiting, load]);

  async function action(work: () => Promise<void>) {
    setBusy(true); setError(null); setNotice(null);
    try { await work(); await load(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "이메일 요청을 처리하지 못했습니다."); }
    finally { setBusy(false); }
  }
  async function schedule(immediate: boolean) {
    if (!reportId) return;
    const signature = `${reportId}|${immediate ? "now" : scheduledAt}`;
    if (request.current?.signature !== signature) request.current = { signature, id: crypto.randomUUID() };
    const date = immediate ? null : new Date(`${scheduledAt}+09:00`).toISOString();
    await apiFetch(`/api/v1/me/briefings/${reportId}/deliveries`, { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ requestId: request.current.id, scheduledAt: date, consent }) });
    request.current = null;
    setConsent(false);
    setNotice(immediate ? "즉시 발송을 요청했습니다. 이력에서 결과를 확인해 주세요." : "예약했습니다. 지정한 한국시간 이후 발송합니다.");
  }
  return <section className="briefing-email" aria-label="브리핑 이메일 발송">
    <h2>브리핑 이메일</h2>
    <p>선택한 브리핑의 PDF를 회원 이메일로 한 번 보냅니다. 예약 후 프로필이나 정책이 바뀌어도 생성 당시의 보고서가 발송됩니다.</p>
    {error && <p className="error-banner" role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {email && <p>수신 주소: {email.email} · {email.verified ? "확인 완료" : "주소 확인 필요"}</p>}
    {email && !email.available && <p>이 계정의 테스트 이메일 발송 설정을 준비 중입니다.</p>}
    {email?.available && !email.verified && <div className="briefing-email-verification">
      <button type="button" disabled={busy} onClick={() => action(async () => {
        await apiFetch("/api/v1/me/briefing-email/verification", { method: "POST" });
        setNotice("확인 메일을 요청했습니다. 메일의 코드를 15분 안에 입력해 주세요.");
      })}>이메일 확인 코드 받기</button>
      <label>메일로 받은 확인 코드<input autoComplete="one-time-code" value={token} onChange={event => setToken(event.target.value.trim())} maxLength={48} /></label>
      <button type="button" disabled={busy || token.length !== 48} onClick={() => action(async () => {
        await apiFetch("/api/v1/me/briefing-email/confirmation", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) });
        setToken(""); setNotice("수신 이메일을 확인했습니다.");
      })}>코드 확인</button>
    </div>}
    {email?.available && email.verified && <div className="briefing-email-schedule">
      {!reportId && <p>발송할 완료된 브리핑을 먼저 선택해 주세요.</p>}
      <label><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} /> 선택한 브리핑 PDF의 1회 이메일 수신에 동의합니다.</label>
      <label>예약 시각 (한국시간)<input type="datetime-local" value={scheduledAt} onChange={event => setScheduledAt(event.target.value)} /></label>
      <p>31일 이내에 예약할 수 있으며, 발송 전에는 이력에서 취소할 수 있습니다.</p>
      <div className="briefing-links">
        <button type="button" disabled={busy || !reportId || !consent} onClick={() => action(() => schedule(true))}>지금 보내기</button>
        <button type="button" disabled={busy || !reportId || !consent || !scheduledAt} onClick={() => action(() => schedule(false))}>예약하기</button>
      </div>
    </div>}
    <h3>최근 발송 이력</h3>
    <button type="button" disabled={busy} onClick={() => action(async () => {})}>이력 새로고침</button>
    {deliveries.length === 0 && <p>발송 이력이 없습니다.</p>}
    <ul className="briefing-deliveries">{deliveries.map(row => <li key={row.id}>
      <strong>{labels[row.status] ?? row.status}</strong> · {dateLabel(row.scheduledAt)}
      {row.sentAt && <p>서버 접수: {dateLabel(row.sentAt)}</p>}
      {row.failureMessage && <p>{row.failureMessage}</p>}
      {row.status === "SCHEDULED" && <button type="button" disabled={busy} onClick={() => action(async () => {
        await apiFetch(`/api/v1/me/briefing-deliveries/${row.id}`, { method: "DELETE" }); setNotice("예약을 취소했습니다.");
      })}>예약 취소</button>}
    </li>)}</ul>
    <p className="muted">메일 서버 접수는 수신함 도착 확인과 다릅니다. 결과 확인 필요 상태는 수신함을 먼저 확인해 주세요. 자동 재발송하지 않습니다.</p>
  </section>;
}
