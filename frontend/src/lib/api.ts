import "server-only";

// 서버 컴포넌트에서 백엔드를 직접 부른다. 브라우저 코드에서는 쓰지 않는다.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | null,
  ) {
    super(`API ${status} ${code ?? ""}`.trim());
  }
}

/** 404면 null, 그 밖의 오류는 ApiError */
export async function apiGet<T>(path: string): Promise<T | null> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Accept: "application/json" },
  });
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    let code: string | null = null;
    try {
      code = ((await res.json()) as { code?: string }).code ?? null;
    } catch {
      // 본문이 JSON이 아니면 코드 없이
    }
    throw new ApiError(res.status, code);
  }
  return (await res.json()) as T;
}
