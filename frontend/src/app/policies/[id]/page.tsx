import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { serverApiBaseUrl } from "../../../lib/api";
import {
  applicationMethodDescription,
  regionLabel,
  splitPolicyText,
  summarizePolicyText,
} from "../../../lib/policy-display";
import type { Policy, RuleNode } from "../../../lib/types";
import { BookmarkButton } from "../../../components/BookmarkButton";
import { ApplicationPeriodDisplay } from "../../../components/ApplicationPeriodDisplay";

export const dynamic = "force-dynamic";

type Params = Promise<{ id: string }>;

async function loadPolicy(id: string): Promise<Policy | null> {
  try {
    const response = await fetch(`${serverApiBaseUrl}/api/v1/policies/${id}`, { cache: "no-store" });
    return response.ok ? ((await response.json()) as Policy) : null;
  } catch {
    return null;
  }
}

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const policy = await loadPolicy((await params).id);
  return policy
    ? { title: `${policy.name} | AIDLINK`, description: policy.description }
    : { title: "정책을 찾을 수 없습니다 | AIDLINK" };
}

function RuleTree({ node }: { node: RuleNode }) {
  if (node.children) {
    const type = node.type === "OR" ? "아래 조건 중 하나" : "아래 조건 모두";
    return (
      <div className="rule-group">
        <strong>{type}</strong>
        <div className="rule-children">
          {node.children.map((child, index) => <RuleTree node={child} key={index} />)}
        </div>
      </div>
    );
  }
  return (
    <div className="rule-leaf">
      <p>{node.description || "공식 안내에서 세부 조건을 확인해 주세요."}</p>
    </div>
  );
}

function PolicyItemList({ items }: { items: string[] }) {
  return (
    <ul className="policy-content-list">
      {items.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}
    </ul>
  );
}

function PolicyFullText({ value, items = splitPolicyText(value) }: { value: string; items?: string[] }) {
  if (items.length <= 1) {
    return <p className="policy-section-copy policy-full-copy">{items[0] ?? value}</p>;
  }

  return <PolicyItemList items={items} />;
}

function ReadablePolicyText({ value }: { value: string }) {
  const items = splitPolicyText(value);

  if (items.length <= 1 && value.length <= 140) {
    return <p className="policy-section-copy">{items[0] ?? "안내 정보가 없습니다."}</p>;
  }

  return (
    <div className="policy-content-block">
      <p className="policy-section-copy policy-summary-copy">{summarizePolicyText(value, 110)}</p>
      <details className="policy-more">
        <summary><span>전체 내용 보기</span><b aria-hidden="true">＋</b></summary>
        <PolicyFullText value={value} items={items} />
      </details>
    </div>
  );
}

function ApplicationDetailValue({ value }: { value: string }) {
  if (value.length <= 110) return <dd>{value}</dd>;

  return (
    <dd>
      <details className="application-more">
        <summary>
          <span>{summarizePolicyText(value, 78)}</span>
          <b aria-hidden="true">
            <span className="expand-label">전체 보기</span>
            <span className="collapse-label">접기</span>
          </b>
        </summary>
        <div className="policy-content-block application-full-content">
          <PolicyFullText value={value} />
        </div>
      </details>
    </dd>
  );
}

function officialUrl(value: string): string | null {
  try {
    const url = new URL(value);
    const placeholder = url.hostname === "example.go.kr" || url.hostname.endsWith(".example.go.kr");
    const retiredPortalRoute = (url.hostname === "www.gov.kr" || url.hostname === "gov.kr")
      && url.pathname.startsWith("/portal/rcvfvrSvc/dtlEx/");
    return (url.protocol === "https:" || url.protocol === "http:") && !placeholder && !retiredPortalRoute
      ? url.toString()
      : null;
  } catch {
    return null;
  }
}

export default async function PolicyDetailPage({ params }: { params: Params }) {
  const policy = await loadPolicy((await params).id);
  if (!policy) notFound();
  const applicationUrl = officialUrl(policy.applicationUrl);
  const sourceUrl = officialUrl(policy.sourceUrl);
  const directApplicationUrl = applicationUrl && applicationUrl !== sourceUrl ? applicationUrl : null;

  return (
    <main className="page-shell shell">
      <Link className="back-link" href="/policies">← 정책 목록</Link>
      <article className="policy-hero">
        <div className="catalog-meta"><span>{policy.category}</span><span>{regionLabel(policy.regionCode)}</span></div>
        <h1>{policy.name}</h1>
        <p>{policy.description}</p>
      </article>

      <section aria-label="정책 핵심 요약" className="policy-overview-grid">
        <article>
          <span className="overview-label">지원 대상</span>
          <strong>{summarizePolicyText(policy.targetDescription, 48)}</strong>
        </article>
        <article>
          <span className="overview-label">지원 혜택</span>
          <strong>{summarizePolicyText(policy.supportContent, 48)}</strong>
        </article>
        <article>
          <span className="overview-label">신청 기간</span>
          <ApplicationPeriodDisplay policy={policy} />
        </article>
      </section>

      <div className="detail-layout">
        <section className="detail-card">
          <section className="policy-info-section">
            <div className="policy-section-heading">
              <span aria-hidden="true">01</span>
              <div><p>BENEFIT</p><h2>어떤 지원을 받나요?</h2></div>
            </div>
            <ReadablePolicyText value={policy.supportContent} />
          </section>

          <section className="policy-info-section">
            <div className="policy-section-heading">
              <span aria-hidden="true">02</span>
              <div><p>ELIGIBILITY</p><h2>누가 신청할 수 있나요?</h2></div>
            </div>
            <ReadablePolicyText value={policy.targetDescription} />
          </section>

          <section className="policy-info-section">
            <div className="policy-section-heading">
              <span aria-hidden="true">03</span>
              <div><p>APPLICATION</p><h2>어떻게 신청하나요?</h2></div>
            </div>
            <dl className="detail-list application-detail-list">
              <div><dt>신청 기간</dt><dd><ApplicationPeriodDisplay policy={policy} /></dd></div>
              {policy.applicationPeriod?.origin === "PROVIDER_REVIEW" && <div><dt>기간 확인 근거</dt><dd>제공기관 공식 공고 확인 · {policy.applicationPeriod.verifiedAt && new Date(policy.applicationPeriod.verifiedAt).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" })}</dd></div>}
              {policy.applicationPeriod?.sourceText && <div><dt>수집된 신청기한 안내</dt><dd>{policy.applicationPeriod.sourceText}</dd></div>}
              <div><dt>신청방법</dt><ApplicationDetailValue value={applicationMethodDescription(policy.applicationMethod)} /></div>
              <div><dt>필요서류</dt><ApplicationDetailValue value={policy.requiredDocuments} /></div>
              <div><dt>제공기관</dt><dd>{policy.provider}</dd></div>
              <div><dt>공식 원문 갱신</dt><dd>{policy.sourceUpdatedAt ? new Date(policy.sourceUpdatedAt).toLocaleDateString("ko-KR") : "정보 없음"}</dd></div>
            </dl>
          </section>
          <div className="detail-actions">
            {directApplicationUrl && (
              <a className="primary-link" href={directApplicationUrl} target="_blank" rel="noreferrer">공식 온라인 신청</a>
            )}
            {sourceUrl && (
              <a className={directApplicationUrl ? "secondary-link" : "primary-link"} href={sourceUrl} target="_blank" rel="noreferrer">
                공식 원문 보기
              </a>
            )}
            {!applicationUrl && !sourceUrl && <span className="empty-rule">현재 연결 가능한 공식 원문을 확인 중입니다. 위 신청방법과 제공기관 안내를 확인해 주세요.</span>}
            <BookmarkButton policyId={policy.id} />
          </div>
          <p className="empty-rule">
            {!sourceUrl ? `정부24의 기존 상세 링크가 현재 연결되지 않습니다. ${policy.provider}에 정책명으로 문의하거나 ` : "다른 안내가 필요하면 "}
            <a href="https://plus.gov.kr/" target="_blank" rel="noreferrer">정부24 홈</a>을 이용해 주세요.
            정책명: {policy.name}
          </p>
        </section>

        <aside className="detail-card rule-card">
          <h2>내 조건과 비교할 항목</h2>
          <p>입력한 내 조건과 아래 기준을 비교해 가능성과 부족한 조건을 설명합니다.</p>
          {policy.rule ? <RuleTree node={policy.rule} /> : <div className="empty-rule">자격조건은 공식 안내에서 확인해 주세요.</div>}
          <Link className="text-link" href="/analysis">내 조건으로 전체 정책 분석 →</Link>
        </aside>
      </div>
    </main>
  );
}

