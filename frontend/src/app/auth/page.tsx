"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../../lib/api";

type Mode = "login" | "register";

const allowedDestinations = ["/policies", "/bookmarks", "/profile", "/analysis", "/simulation", "/admin"];

function destinationAfterLogin(): string {
  const requested = new URLSearchParams(window.location.search).get("next");
  if (!requested) return "/policies";

  const destination = new URL(requested, window.location.origin);
  const allowed = destination.origin === window.location.origin
    && allowedDestinations.some((path) => destination.pathname === path || destination.pathname.startsWith(`${path}/`));

  return allowed
    ? `${destination.pathname}${destination.search}${destination.hash}`
    : "/policies";
}

export default function AuthPage() {
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordHovered, setPasswordHovered] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<{ authenticated: boolean }>("/api/v1/auth/me")
      .then((session) => {
        if (session.authenticated) window.location.replace(destinationAfterLogin());
      })
      .catch(() => undefined);
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (mode === "register") {
        await apiFetch<void>("/api/v1/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        });
      }
      await apiFetch<{ email: string }>("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      window.location.href = destinationAfterLogin();
    } catch (caught) {
      setPassword("");
      setPasswordHovered(false);
      setPasswordVisible(false);
      setError(caught instanceof ApiError ? caught.message : "인증 요청을 처리하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function changeMode() {
    setMode((current) => current === "login" ? "register" : "login");
    setPassword("");
    setPasswordHovered(false);
    setPasswordVisible(false);
    setError(null);
  }

  const isLogin = mode === "login";
  const revealPassword = passwordHovered || passwordVisible;

  return (
    <main className="auth-page">
      <div className="auth-shell shell">
        <section className="auth-copy" aria-labelledby="auth-title">
          <p className="auth-eyebrow"><span aria-hidden="true"></span>AIDLINK ACCOUNT</p>
          <h1 id="auth-title">
            {isLogin ? <>내 조건에 맞는<br /><em>정책을 이어서.</em></> : <>새로운 지원을<br /><em>놓치지 않도록.</em></>}
          </h1>
          <p>
            {isLogin
              ? "저장해 둔 조건으로 정책 찾기와 맞춤 분석을 계속 이용하세요."
              : "계정을 만들고 내 조건을 저장하면 정책 탐색부터 변화 비교까지 한곳에서 이어갈 수 있어요."}
          </p>
          <ul className="auth-benefits" aria-label="계정으로 이용할 수 있는 기능">
            <li><span aria-hidden="true">✓</span> 정책 검색과 자격조건 확인</li>
            <li><span aria-hidden="true">✓</span> 내 조건을 반영한 맞춤 분석</li>
            <li><span aria-hidden="true">✓</span> 상황 변화에 따른 정책 비교</li>
          </ul>
        </section>

        <section className="auth-card" key={mode} aria-label={isLogin ? "로그인 양식" : "회원가입 양식"}>
          <div className="auth-card-heading">
            <span>{isLogin ? "LOGIN" : "JOIN"}</span>
            <h2>{isLogin ? "로그인" : "계정 만들기"}</h2>
            <p>{isLogin ? "AIDLINK에서 내 정책 탐색을 이어가세요." : "이메일과 비밀번호만으로 바로 시작할 수 있어요."}</p>
          </div>
          <form onSubmit={submit}>
            <label>
              <span>이메일</span>
              <input required type="email" autoComplete="email" placeholder="name@example.com" value={email} onChange={(event) => setEmail(event.target.value)} />
            </label>
            <div className="auth-password-control">
              <label htmlFor="auth-password">비밀번호</label>
              <span className="password-field">
                <input
                  id="auth-password"
                  required
                  minLength={isLogin ? undefined : 8}
                  type={revealPassword ? "text" : "password"}
                  autoComplete={isLogin ? "current-password" : "new-password"}
                  placeholder="비밀번호를 입력해주세요"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  className="password-visibility"
                  type="button"
                  aria-label={revealPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                  aria-pressed={passwordVisible}
                  onPointerEnter={(event) => {
                    if (event.pointerType === "mouse") setPasswordHovered(true);
                  }}
                  onPointerLeave={() => setPasswordHovered(false)}
                  onClick={() => setPasswordVisible((current) => !current)}
                >
                  {revealPassword ? (
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
                      <circle cx="12" cy="12" r="2.75" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M3 3l18 18" />
                      <path d="M10.6 6.15A9.74 9.74 0 0 1 12 6c6 0 9.5 6 9.5 6a15.5 15.5 0 0 1-2.1 2.8M6.25 7.35C3.8 9.2 2.5 12 2.5 12s3.5 6 9.5 6a9.8 9.8 0 0 0 3.25-.55M9.9 9.9a3 3 0 0 0 4.2 4.2" />
                    </svg>
                  )}
                </button>
              </span>
            </div>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="primary auth-submit" disabled={loading} type="submit">
              <span>{loading ? "처리 중…" : isLogin ? "로그인" : "가입 후 로그인"}</span>
              {!loading && <span aria-hidden="true">→</span>}
            </button>
          </form>
          <div className="auth-mode-row">
            <span>{isLogin ? "AIDLINK가 처음인가요?" : "이미 계정이 있나요?"}</span>
            <button className="mode-toggle" type="button" onClick={changeMode}>
              {isLogin ? "계정 만들기" : "로그인하기"} <span aria-hidden="true">↗</span>
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}
