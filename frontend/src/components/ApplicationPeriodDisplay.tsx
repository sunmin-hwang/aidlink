import { applicationPeriod, applicationPeriodNotice, type ApplicationPeriodInput } from "../lib/policy-display";

export function ApplicationPeriodDisplay({ policy }: { policy: ApplicationPeriodInput }) {
  const notice = applicationPeriodNotice(policy);
  return (
    <span className="application-period-display">
      <span className="application-period-label">{applicationPeriod(policy)}</span>
      {notice && <small className="application-period-notice">{notice}</small>}
    </span>
  );
}
