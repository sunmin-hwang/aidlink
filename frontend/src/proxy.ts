import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

type Session = {
  authenticated: boolean;
  roles: string[];
};

const anonymousSession: Session = { authenticated: false, roles: [] };

const backendApiBaseUrl =
  process.env.BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  "http://localhost:8080";

async function readSession(request: NextRequest): Promise<Session> {
  const cookie = request.headers.get("cookie");
  if (!cookie) return anonymousSession;

  try {
    const response = await fetch(`${backendApiBaseUrl}/api/v1/auth/me`, {
      cache: "no-store",
      headers: { cookie },
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) return anonymousSession;

    const session = (await response.json()) as Session;
    return {
      authenticated: session.authenticated === true,
      roles: Array.isArray(session.roles)
        ? session.roles.filter((role): role is string => typeof role === "string")
        : [],
    };
  } catch {
    return anonymousSession;
  }
}

export async function proxy(request: NextRequest) {
  const session = await readSession(request);
  const requestedPath = `${request.nextUrl.pathname}${request.nextUrl.search}`;

  if (!session.authenticated) {
    const loginUrl = new URL("/auth", request.url);
    loginUrl.searchParams.set(
      "next",
      request.nextUrl.pathname.startsWith("/dashboard") ? "/policies" : requestedPath,
    );
    return NextResponse.redirect(loginUrl);
  }

  if (
    request.nextUrl.pathname.startsWith("/admin")
    && !session.roles.includes("ROLE_ADMIN")
  ) {
    return NextResponse.redirect(new URL("/policies", request.url));
  }

  if (request.nextUrl.pathname.startsWith("/dashboard")) {
    return NextResponse.redirect(new URL("/policies", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/policies/:path*",
    "/bookmarks/:path*",
    "/profile/:path*",
    "/analysis/:path*",
    "/briefings/:path*",
    "/simulation/:path*",
    "/admin/:path*",
  ],
};
