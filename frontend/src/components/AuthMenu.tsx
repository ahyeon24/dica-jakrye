"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { apiSend } from "@/lib/client";
import type { Me } from "@/lib/types";

/** 헤더 오른쪽: 로그인 안 했으면 "로그인", 했으면 닉네임과 로그아웃 */
export function AuthMenu() {
  const pathname = usePathname();
  const router = useRouter();
  // undefined = 확인 중, null = 로그인 안 함
  const [me, setMe] = useState<Me | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/me", { credentials: "same-origin" })
      .then((res) => (res.ok ? (res.json() as Promise<Me>) : null))
      .then((data) => {
        if (!cancelled) setMe(data);
      })
      .catch(() => {
        if (!cancelled) setMe(null);
      });
    return () => {
      cancelled = true;
    };
  }, [pathname]);

  async function logout() {
    try {
      await apiSend("POST", "/api/auth/logout");
    } finally {
      setMe(null);
      router.refresh();
    }
  }

  if (me === undefined) return <span className="h-5 w-16" aria-hidden />;

  if (!me) {
    // 로그인·가입 화면에서는 돌아갈 곳으로 자기 자신을 넘기지 않는다
    const back = pathname === "/login" || pathname === "/signup" ? "/" : pathname;
    return (
      <Link href={`/login?redirect=${encodeURIComponent(back)}`} className="text-sm text-muted hover:text-foreground">
        로그인
      </Link>
    );
  }

  return (
    <div className="flex items-center gap-3 text-sm">
      <Link href={`/users/${encodeURIComponent(me.nickname)}`} className="font-medium hover:underline">
        {me.nickname}
      </Link>
      <button type="button" onClick={logout} className="text-muted hover:text-foreground">
        로그아웃
      </button>
    </div>
  );
}
