"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import type { SearchItem } from "@/lib/types";
import { cameraTitle } from "@/lib/format";
import { Thumb } from "./Thumb";

/** 기종 검색창. 입력하면 아래로 결과가 뜬다. (와이어프레임 1쪽) */
export function CameraSearch() {
  const router = useRouter();
  const listId = useId();
  const [q, setQ] = useState("");
  // 결과가 어떤 검색어에 대한 것인지 같이 저장한다 (지운 검색어의 결과가 남아 보이지 않게)
  const [result, setResult] = useState<{ query: string; items: SearchItem[] } | null>(null);
  const [error, setError] = useState(false);
  const query = q.trim();
  const items = result && result.query === query ? result.items : null;

  useEffect(() => {
    if (!query) {
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`/api/cameras/search?q=${encodeURIComponent(query.slice(0, 50))}`, {
          signal: controller.signal,
        });
        if (!res.ok) throw new Error(String(res.status));
        const data = (await res.json()) as { items: SearchItem[] };
        setResult({ query, items: data.items });
        setError(false);
      } catch (e) {
        if ((e as Error).name !== "AbortError") setError(true);
      }
    }, 200);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  return (
    <div className="relative">
      <input
        type="search"
        value={q}
        maxLength={50}
        onChange={(e) => setQ(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && items && items.length > 0) {
            router.push(`/cameras/${items[0].slug}`);
          }
        }}
        placeholder="기종 검색 (예: 익서스, IXY, 케녹스)"
        aria-label="기종 검색"
        aria-controls={listId}
        className="w-full rounded-lg border border-line bg-card px-4 py-3 text-base outline-none focus:border-foreground"
      />
      {query && (
        <div id={listId} className="absolute z-10 mt-1 w-full overflow-hidden rounded-lg border border-line bg-card shadow-lg">
          {error && <p className="px-4 py-3 text-sm text-muted">검색하지 못했어요. 잠시 후 다시 시도해 주세요.</p>}
          {!error && items?.length === 0 && (
            <p className="px-4 py-3 text-sm text-muted">
              아직 등록되지 않은 기종이에요. 작례를 올리면 확인 후 추가돼요.
            </p>
          )}
          {!error &&
            items?.map((it) => (
              <Link
                key={it.slug}
                href={`/cameras/${it.slug}`}
                className="flex items-center gap-3 px-4 py-2 hover:bg-background"
              >
                <Thumb src={it.coverThumbnailUrl} alt="" className="h-10 w-10 shrink-0 rounded" />
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">{cameraTitle(it.name, it.nameKo)}</div>
                  <div className="truncate text-xs text-muted">
                    {it.brandName}
                    {it.matchedAlias && ` · "${it.matchedAlias}"로 찾음`} · 작례 {it.photoCount}장
                  </div>
                </div>
              </Link>
            ))}
        </div>
      )}
    </div>
  );
}
