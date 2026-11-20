import {
  eligibilityGuidance,
  eligibilityLabel,
  evaluationLabel,
} from "../lib/policy-display";
import type { Analysis, EvaluationStatus } from "../lib/types";

const conditionIcon: Record<EvaluationStatus, string> = {
  PASS: "✓",
  FAIL: "×",
  UNKNOWN: "?",
};

export function AnalysisCard({ analysis, open = false }: { analysis: Analysis; open?: boolean }) {
  return (
    <details className="policy-card" open={open}>
      <summary>
        <div>
          <span className="category">{analysis.category}</span>
          <h3>{analysis.policyName}</h3>
          <p>{analysis.provider}</p>
        </div>
        <span className={`status-pill ${analysis.eligibilityStatus.toLowerCase()}`}>
          {eligibilityLabel(analysis.eligibilityStatus)}
        </span>
      </summary>
      <div className="policy-detail">
        <p className="guidance">{eligibilityGuidance(analysis.eligibilityStatus)}</p>
        {analysis.blockingConditions.length > 0 && (
          <p className="blocking-copy">
            실제 판정을 막는 조건 {analysis.blockingConditions.length}개를 우선 확인하세요.
          </p>
        )}
        <ul>
          {analysis.conditions.map((condition, index) => (
            <li className={condition.status.toLowerCase()} key={`${condition.field}-${index}`}>
              <span aria-hidden="true" className="condition-icon">{conditionIcon[condition.status]}</span>
              <div>
                <strong>{condition.description}</strong>
                <small>{evaluationLabel(condition.status)} · {condition.message}</small>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </details>
  );
}
