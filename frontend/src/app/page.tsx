import Link from "next/link";
import { Suspense } from "react";
import { CameraSearch } from "@/components/CameraSearch";
import { apiGet } from "@/lib/api";
import type { BrandItem } from "@/lib/types";

export default function Home() {
  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <h1 className="text-xl font-bold">빈티지 디카, 사기 전에 작례부터</h1>
        <p className="text-sm text-muted">그 기종으로 진짜 찍은 사진을 모아 보세요.</p>
      </section>

      <CameraSearch />

      <section className="space-y-3">
        <h2 className="text-base font-bold">브랜드</h2>
        <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
          <BrandList />
        </Suspense>
      </section>

      {/* 최근 올라온 작례는 업로드 기능을 만든 뒤에 붙인다 (PRD F1) */}
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
