import Link from "next/link";
import { Suspense } from "react";
import { CameraSearch } from "@/components/CameraSearch";
import { PhotoGrid } from "@/components/PhotoGrid";
import { apiGet } from "@/lib/api";
import type { BrandItem, Page, PhotoSummary } from "@/lib/types";

export default function Home() {
  return (
    <div className="space-y-8">
      {/* 화면에는 안 보이지만 검색엔진·화면 읽기 프로그램을 위해 페이지 제목은 둔다 */}
      <h1 className="sr-only">디카작례 — 빈티지 디카 작례 모음</h1>

      <CameraSearch />

      <section className="space-y-3">
        <h2 className="text-base font-bold">브랜드</h2>
        <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
          <BrandList />
        </Suspense>
      </section>

      <Suspense fallback={null}>
        <RecentPhotos />
      </Suspense>
    </div>
  );
}

async function BrandList() {
  const brands = (await apiGet<BrandItem[]>("/api/brands")) ?? [];
  return (
    <div className="flex flex-wrap gap-2">
      {brands.map((b) => (
        <Link
          key={b.slug}
          href={`/brands/${b.slug}`}
          className="rounded-full border border-line px-3 py-1.5 text-sm hover:border-foreground"
        >
          {b.name}
          {b.nameKo && <span className="text-muted"> {b.nameKo}</span>}
          <span className="ml-1 text-xs text-muted">{b.cameraCount}</span>
        </Link>
      ))}
    </div>
  );
}

/** 최근 올라온 작례 (사용자 업로드). 아직 없으면 영역을 숨긴다 (API.md 3장) */
async function RecentPhotos() {
  const page = await apiGet<Page<PhotoSummary>>("/api/photos/recent");
  if (!page || page.items.length === 0) return null;
  return (
    <section className="space-y-3">
      <h2 className="text-base font-bold">최근 올라온 작례</h2>
      <PhotoGrid endpoint="/api/photos/recent" initial={page} />
    </section>
  );
}
