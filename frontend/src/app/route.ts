import { readFile } from "node:fs/promises";
import path from "node:path";

export const runtime = "nodejs";

export async function GET() {
  const landingPath = path.join(process.cwd(), "public", "landing", "index.html");
  const source = await readFile(landingPath, "utf8");
  const html = source.replaceAll("__CURRENT_YEAR__", String(new Date().getFullYear()));

  return new Response(html, {
    headers: {
      "Cache-Control": "public, max-age=0",
      "Content-Type": "text/html; charset=utf-8",
    },
  });
}
