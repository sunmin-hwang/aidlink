// Offline audit of a read-only DB projection. Does not call APIs or update data.
import { readFileSync } from "node:fs";
import process from "node:process";
import { applicationPeriod } from "../src/lib/policy-display.ts";

const rows = readFileSync(process.argv[2], "utf8").trim().split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
const summary = { total: rows.length, byLifecycle: {}, examples: {} };
for (const row of rows) {
  const label = applicationPeriod(row);
  const group = summary.byLifecycle[row.lifecycleStatus] ??= { total: 0, labels: {}, missingBothDates: 0 };
  group.total++;
  group.labels[label] = (group.labels[label] ?? 0) + 1;
  if (!row.applicationStart && !row.applicationEnd) group.missingBothDates++;
  if (row.lifecycleStatus === "ACTIVE") {
    const examples = summary.examples[label] ??= [];
    if (examples.length < 3) examples.push({ id: row.id, name: row.name });
  }
}
const taxPolicy = rows.find((row) => row.externalId === "GOV24:105100000001");
if (taxPolicy && applicationPeriod(taxPolicy) === "상시 신청") throw new Error("근로·자녀장려금이 상시 신청으로 잘못 분류됐습니다.");
process.stdout.write(JSON.stringify(summary, null, 2) + "\n");
