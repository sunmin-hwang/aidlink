"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AuthControls } from "./AuthControls";

export function Navigation() {
  const pathname = usePathname();
  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  if (pathname === "/auth") {
    return (
      <header className="auth-site-header">
        <nav className="auth-nav shell" aria-label="인증 화면">
          <Link className="brand auth-brand" href="/" aria-label="AIDLINK 서비스 소개로 돌아가기">
            <svg aria-hidden="true" viewBox="0 0 32 32">
              <path d="m13 9.5-2.5 2.5a5.5 5.5 0 0 0 7.8 7.8l2.4-2.4M19 22.5l2.5-2.5a5.5 5.5 0 0 0-7.8-7.8l-2.4 2.4M12.5 19.5l7-7" />
            </svg>
            <span className="brand-name">AID<span>LINK</span></span>
          </Link>
          <Link className="auth-back-link" href="/">
            서비스 소개로 돌아가기 <span aria-hidden="true">↗</span>
          </Link>
        </nav>
      </header>
    );
  }

  return (
    <header className="site-header">
      <nav className="nav shell" aria-label="주요 메뉴">
        <Link className="brand" href="/policies">
          <svg aria-hidden="true" viewBox="0 0 32 32">
            <path d="m13 9.5-2.5 2.5a5.5 5.5 0 0 0 7.8 7.8l2.4-2.4M19 22.5l2.5-2.5a5.5 5.5 0 0 0-7.8-7.8l-2.4 2.4M12.5 19.5l7-7" />
          </svg>
          <span className="brand-name">AID<span>LINK</span></span>
        </Link>
        <div className="nav-links">
          <Link aria-current={isActive("/policies") ? "page" : undefined} className={isActive("/policies") ? "active" : undefined} href="/policies">정책 찾기</Link>
          <Link aria-current={isActive("/bookmarks") ? "page" : undefined} className={isActive("/bookmarks") ? "active" : undefined} href="/bookmarks">저장 정책</Link>
          <Link aria-current={isActive("/analysis") ? "page" : undefined} className={isActive("/analysis") ? "active" : undefined} href="/analysis">맞춤 분석</Link>
          <Link aria-current={isActive("/simulation") ? "page" : undefined} className={isActive("/simulation") ? "active" : undefined} href="/simulation">상황 변화</Link>
          <span className="nav-planned">정책 브리핑 <small>준비 중</small></span>
        </div>
        <div className="nav-actions">
          <div className="desktop-auth-controls">
            <AuthControls />
          </div>
          <details className="mobile-navigation">
            <summary>
              <span className="mobile-menu-symbol" aria-hidden="true"></span>
              <span>메뉴</span>
            </summary>
            <div className="mobile-navigation-panel">
              <Link aria-current={isActive("/policies") ? "page" : undefined} className={isActive("/policies") ? "active" : undefined} href="/policies">정책 찾기</Link>
              <Link aria-current={isActive("/bookmarks") ? "page" : undefined} className={isActive("/bookmarks") ? "active" : undefined} href="/bookmarks">저장 정책</Link>
              <Link aria-current={isActive("/analysis") ? "page" : undefined} className={isActive("/analysis") ? "active" : undefined} href="/analysis">맞춤 분석</Link>
              <Link aria-current={isActive("/simulation") ? "page" : undefined} className={isActive("/simulation") ? "active" : undefined} href="/simulation">상황 변화</Link>
              <span className="mobile-nav-planned">정책 브리핑 <small>준비 중</small></span>
              <div className="mobile-auth-controls">
                <AuthControls />
              </div>
            </div>
          </details>
        </div>
      </nav>
    </header>
  );
}

