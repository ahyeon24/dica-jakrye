"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Me } from "@/lib/types";

/** 내 프로필일 때만 보이는 버튼. 서버 렌더링은 로그인을 모르므로 브라우저에서 확인한다 (와이어프레임 5쪽) */
export function ProfileOwnerActions({ nickname }: { nickname: string }) {
  const [isMe, setIsMe] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/me", { credentials: "same-origin" })
      .then((res) => (res.ok ? (res.json() as Promise<Me>) : null))
      .then((me) => {
        if (!cancelled) setIsMe(me?.nickname === nickname);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [nickname]);

  if (!isMe) return null;
  // 설정(닉네임 변경·탈퇴)은 탈퇴 기능을 만들 때 붙인다
  return (
    <Link href="/upload" className="shrink-0 rounded-lg bg-foreground px-3 py-2 text-sm font-medium text-background">
      작례 올리기
    </Link>
  );
}
