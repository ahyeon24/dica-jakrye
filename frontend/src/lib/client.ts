// 브라우저에서 부르는 API 도우미. 서버 컴포넌트는 lib/api.ts를 쓴다.

/** 로그인 시작 주소의 앞부분. 로그인은 백엔드(api.{도메인})가 직접 처리한다 */
export const API_ORIGIN = process.env.NEXT_PUBLIC_API_ORIGIN ?? "http://localhost:8080";

/** 로그인 후 돌아올 경로는 우리 사이트 내부 경로만 (백엔드도 같은 검사를 한다, SECURITY.md 6장) */
export function safeRedirect(path: string | null | undefined): string {
  if (!path || !path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) return "/";
  return path;
}

export function loginUrl(provider: "google" | "kakao", redirect: string): string {
  return `${API_ORIGIN}/oauth2/authorization/${provider}?redirect=${encodeURIComponent(safeRedirect(redirect))}`;
}

function readCookie(name: string): string | null {
  const hit = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null;
}

/** CSRF 쿠키가 없으면 먼저 받아 온다 (API.md 1.3) */
async function csrfToken(): Promise<string | null> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

export class ClientApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | null,
    message: string,
  ) {
    super(message);
  }
}

/** 쓰기 요청(POST·PATCH·DELETE). 실패하면 서버의 { code, message }를 담은 ClientApiError */
export async function apiSend<T = void>(
  method: "POST" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
  init?: { keepalive?: boolean },
): Promise<T> {
  const token = await csrfToken();
  const res = await fetch(path, {
    method,
    credentials: "same-origin",
    keepalive: init?.keepalive,
    headers: {
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(token ? { "X-XSRF-TOKEN": token } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let code: string | null = null;
    let message = "잠시 후 다시 시도해 주세요.";
    try {
      const data = (await res.json()) as { code?: string; message?: string };
      code = data.code ?? null;
      message = data.message ?? message;
    } catch {
      // 본문이 JSON이 아니면 기본 문구
    }
    throw new ClientApiError(res.status, code, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/**
 * 작례 올리기. 큰 파일이 프론트 서버(Vercel)를 거치지 않도록 API 서버로 바로 보낸다.
 * 쿠키(세션·CSRF)를 같이 보내야 해서 credentials: "include" (API 서버 CORS가 우리 프론트만 허용)
 */
export async function uploadPhoto<T>(file: File, agreedToUploadPolicy: boolean): Promise<T> {
  const token = await csrfToken();
  const form = new FormData();
  form.append("file", file);
  form.append("agreedToUploadPolicy", String(agreedToUploadPolicy));
  const res = await fetch(`${API_ORIGIN}/api/photos`, {
    method: "POST",
    credentials: "include",
    headers: token ? { "X-XSRF-TOKEN": token } : {},
    body: form,
  });
  if (!res.ok) {
    let code: string | null = null;
    let message = "올리지 못했어요. 잠시 후 다시 시도해 주세요.";
    try {
      const data = (await res.json()) as { code?: string; message?: string };
      code = data.code ?? null;
      message = data.message ?? message;
    } catch {
      if (res.status === 413) message = "20MB보다 큰 파일은 올릴 수 없어요.";
    }
    throw new ClientApiError(res.status, code, message);
  }
  return (await res.json()) as T;
}
