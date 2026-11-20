"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../lib/api";

export function BookmarkButton({ policyId }: { policyId: number }) {
  const [bookmarked, setBookmarked] = useState(false);
  const [authenticated, setAuthenticated] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    apiFetch<{ authenticated: boolean }>("/api/v1/auth/me")
      .then(async (session) => {
        setAuthenticated(session.authenticated);
        if (!session.authenticated) return;
        const status = await apiFetch<{ bookmarked: boolean }>(`/api/v1/me/bookmarks/${policyId}`);
        setBookmarked(status.bookmarked);
      })
      .catch(() => setAuthenticated(false));
  }, [policyId]);

  async function toggle() {
    setLoading(true);
    try {
      await apiFetch<void>(`/api/v1/me/bookmarks/${policyId}`, {
        method: bookmarked ? "DELETE" : "POST",
      });
      setBookmarked((current) => !current);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setAuthenticated(false);
    } finally {
      setLoading(false);
    }
  }

  if (authenticated === false) return <Link className="secondary-link" href="/auth">로그인 후 저장</Link>;
  return <button className="bookmark-button" type="button" disabled={authenticated === null || loading} onClick={toggle}>{bookmarked ? "저장됨 ✓" : "정책 저장"}</button>;
}
