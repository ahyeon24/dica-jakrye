"use client";

import Link from "next/link";
import { useState } from "react";
import type { Page, PhotoSummary } from "@/lib/types";
import { Thumb } from "./Thumb";

/**
 * 작례 격자 + "더 보기". 첫 페이지는 서버에서 받아 오고, 다음 페이지는 브라우저에서 커서로 이어 받는다.
 * 기종 페이지·홈·프로필이 같이 쓴다.
 * 휴대폰 3칸, 768px 이상 4칸, 1024px 이상 6칸 (24장이 모두 나누어떨어짐, ARCHITECTURE.md 2.1)
 */
export function PhotoGrid({
  endpoint,
  query = "",
  initial,
  empty,
}: {
  endpoint: string; // 다음 페이지를 받을 API 경로 (예: "/api/photos/recent")
  query?: string; // 기종 페이지의 flash·originalOnly 필터. 작례 상세로도 넘겨 같은 순서로 넘겨 보게 한다
  initial: Page<PhotoSummary>;
  empty?: { title: string; hint?: string };
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
      const res = await fetch(`${endpoint}?${params}`);
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
        <p className="font-medium">{empty?.title ?? "아직 작례가 없어요"}</p>
        {empty?.hint && <p className="mt-1 text-sm text-muted">{empty.hint}</p>}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <ul className="grid grid-cols-3 gap-0.5 md:grid-cols-4 lg:grid-cols-6">
        {items.map((p) => (
          <li key={p.id} className="relative">
            <Link href={`/photos/${p.id}${query ? `?${query}` : ""}`} className="block">
              <Thumb src={p.thumbnailUrl} alt={p.credit ? `${p.credit}의 작례` : "작례"} className="aspect-square w-full" />
            </Link>
            {/* 미분류 작례는 프로필에만 나온다 (PRD F6) */}
            {!p.camera && (
              <span className="pointer-events-none absolute bottom-1 left-1 rounded bg-black/60 px-1 text-[10px] leading-4 text-white">
                기종 확인 중
              </span>
            )}
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
