export const clientApiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const serverApiBaseUrl =
  process.env.BACKEND_API_BASE_URL ?? clientApiBaseUrl;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

export async function parseApiResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new ApiError(body?.message ?? `API 요청에 실패했습니다. (${response.status})`, response.status);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

type CsrfResponse = { token: string; headerName: string };

async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch(`${clientApiBaseUrl}/api/v1/auth/csrf`, {
    credentials: "include",
  });
  const csrf = await parseApiResponse<CsrfResponse>(response);
  return { [csrf.headerName]: csrf.token };
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const mutating = !["GET", "HEAD", "OPTIONS"].includes(method);
  const headers = new Headers(init.headers);

  if (mutating) {
    const csrf = await csrfHeaders();
    Object.entries(csrf).forEach(([name, value]) => headers.set(name, value));
  }

  const response = await fetch(`${clientApiBaseUrl}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });
  return parseApiResponse<T>(response);
}

