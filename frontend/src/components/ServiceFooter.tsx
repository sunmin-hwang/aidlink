"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export function ServiceFooter() {
  const pathname = usePathname();

  if (pathname === "/auth") {
    return null;
  }

  return (
    <footer className="service-footer">
      <div className="shell service-footer-main">
        <Link className="brand service-footer-brand" href="/policies" aria-label="AIDLINK 정책 찾기">
          <svg aria-hidden="true" viewBox="0 0 32 32">
            <path d="m13 9.5-2.5 2.5a5.5 5.5 0 0 0 7.8 7.8l2.4-2.4M19 22.5l2.5-2.5a5.5 5.5 0 0 0-7.8-7.8l-2.4 2.4M12.5 19.5l7-7" />
          </svg>
          <span>AIDLINK</span>
        </Link>
        <p>정책은 무료로 찾고,<br />내게 맞는 새로운 기회는 놓치지 않게.</p>
        <nav aria-label="하단 메뉴">
          <Link href="/policies">무료 정책 검색</Link>
          <Link href="/bookmarks">저장 정책</Link>
          <Link href="/analysis">맞춤 분석</Link>
          <Link href="/simulation">상황 변화</Link>
        </nav>
      </div>
      <div className="shell service-footer-bottom">
        <span>© {new Date().getFullYear()} AIDLINK</span>
        <Link href="/">서비스 소개로 돌아가기</Link>
      </div>
    </footer>
  );
}
