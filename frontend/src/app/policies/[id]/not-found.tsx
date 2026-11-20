import Link from "next/link";

export default function PolicyNotFound() {
  return (
    <main className="page-shell shell">
      <div className="empty-state">
        <h1>정책을 찾을 수 없습니다.</h1>
        <p>정책 서버 연결 또는 정책 번호를 확인해 주세요.</p>
        <Link className="primary-link" href="/policies">정책 목록으로</Link>
      </div>
    </main>
  );
}
