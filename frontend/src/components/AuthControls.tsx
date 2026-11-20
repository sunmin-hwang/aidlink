"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch } from "../lib/api";

type Session = { authenticated: boolean; email: string; roles: string[] };

export function AuthControls() {
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    apiFetch<Session>("/api/v1/auth/me")
      .then(setSession)
      .catch(() => setSession({ authenticated: false, email: "", roles: [] }));
  }, []);

  async function logout() {
    await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
    window.location.href = "/";
  }

  if (!session) return <span className="auth-loading">확인 중</span>;
  if (!session.authenticated) return <Link className="auth-link" href="/auth?next=/policies">로그인</Link>;

  return (
    <div className="auth-controls">
      {session.roles.includes("ROLE_ADMIN") && <Link className="admin-link" href="/admin">관리</Link>}
      <Link className="account-link" href="/profile">내 프로필</Link>
      <button type="button" onClick={logout}>로그아웃</button>
    </div>
  );
}
