"use client";

import Link from "next/link";
import { useState } from "react";
import type { Page, PhotoSummary } from "@/lib/types";
import { Thumb } from "./Thumb";

/**
 * 작례 격자 + "더 보기". 첫 페이지는 서버에서 받아 오고, 다음 페이지는 브라우저에서 커서로 이어 받는다.
 * 휴대폰 3칸, 768px 이상 4칸, 1024px 이상 6칸 (24장이 모두 나누어떨어짐, ARCHITECTURE.md 2.1)
 */
export function PhotoGrid({
  slug,
  query,
  initial,
}: {
  slug: string;
  query: string; // flash·originalOnly 필터 (예: "flash=true")
  initial: Page<PhotoSummary>;
}) {
  const [items, setItems] = useState(initial.items);
  const [cursor, setCursor] = useState(initial.nextCursor);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  async function loadMore() {
    if (!cursor || loading) return;
    setLoading(true);
    setError(false);
    try {
      const params = new URLSearchParams(query);
      params.set("cursor", cursor);
      const res = await fetch(`/api/cameras/${encodeURIComponent(slug)}/photos?${params}`);
      if (!res.ok) throw new Error(String(res.status));
      const page = (await res.json()) as Page<PhotoSummary>;
      setItems((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  if (items.length === 0) {
    return (
      <div className="rounded-lg border border-line px-4 py-10 text-center">
        <p className="font-medium">아직 작례가 없어요</p>
        <p className="mt-1 text-sm text-muted">이 카메라로 찍은 원본 사진이 있다면 첫 작례를 올려주세요.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <ul className="grid grid-cols-3 gap-0.5 md:grid-cols-4 lg:grid-cols-6">
        {items.map((p) => (
          <li key={p.id} className="relative">
            <Link href={`/photos/${p.id}`} className="block">
              <Thumb src={p.thumbnailUrl} alt={p.credit ? `${p.credit}의 작례` : "작례"} className="aspect-square w-full" />
            </Link>
            <div className="pointer-events-none absolute bottom-1 left-1 flex gap-0.5">
              {p.isOriginalSize === false && <Badge>줄인 사진</Badge>}
            </div>
          </li>
        ))}
      </ul>
      {cursor && (
        <button
          type="button"
          onClick={loadMore}
          disabled={loading}
          className="w-full rounded-lg border border-line py-3 text-sm hover:border-foreground disabled:opacity-50"
        >
          {loading ? "불러오는 중…" : "더 보기"}
        </button>
      )}
      {error && <p className="text-center text-sm text-muted">불러오지 못했어요. 다시 눌러 주세요.</p>}
    </div>
  );
}

function Badge({ children }: { children: React.ReactNode }) {
  return <span className="rounded bg-black/60 px-1 text-[10px] leading-4 text-white">{children}</span>;
}
