"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Me } from "@/lib/types";
import { Thumb } from "./Thumb";

type Hidden = { id: number; thumbnailUrl: string; hiddenAt: string };

/** 내 프로필에서만: 신고로 가려진 내 작례 알림 (올린 사람에게 알리기, PRD F8) */
export function MyHiddenPhotos({ nickname }: { nickname: string }) {
  const [items, setItems] = useState<Hidden[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const meRes = await fetch("/api/me", { credentials: "same-origin" }).catch(() => null);
      if (!meRes?.ok) return;
      const me = (await meRes.json()) as Me;
      if (me.nickname !== nickname) return;
      const res = await fetch("/api/me/hidden-photos", { credentials: "same-origin" }).catch(() => null);
      if (!res?.ok) return;
      const data = (await res.json()) as { items: Hidden[] };
      if (!cancelled) setItems(data.items);
    })();
    return () => {
      cancelled = true;
    };
  }, [nickname]);

  if (items.length === 0) return null;
  return (
    <div className="space-y-2 rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
      <p className="font-medium">신고로 가려진 작례 {items.length}장</p>
      <p>
        운영자가 확인한 뒤 최대 30일 안에 다시 공개하거나 삭제해요. 지금은 나와 운영자만 볼 수 있어요. 이의가 있으면{" "}
        <a href="mailto:dicajakrye@gmail.com" className="underline">
          dicajakrye@gmail.com
        </a>
        으로 알려 주세요.
      </p>
      <ul className="flex flex-wrap gap-1">
        {items.map((p) => (
          <li key={p.id}>
            <Link href={`/photos/${p.id}`}>
              <Thumb src={p.thumbnailUrl} alt="가려진 작례" className="h-14 w-14 rounded" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
