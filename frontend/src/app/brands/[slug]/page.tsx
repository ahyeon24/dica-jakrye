import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { Thumb } from "@/components/Thumb";
import { apiGet } from "@/lib/api";
import { brandTitle, cameraTitle } from "@/lib/format";
import type { BrandDetail } from "@/lib/types";

export default function BrandPage({ params }: PageProps<"/brands/[slug]">) {
  return (
    <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
      <BrandContent params={params} />
    </Suspense>
  );
}

async function BrandContent({ params }: Pick<PageProps<"/brands/[slug]">, "params">) {
  const { slug } = await params;
  const brand = await apiGet<BrandDetail>(`/api/brands/${encodeURIComponent(slug)}`);
  if (!brand) notFound();

  return (
    <div className="space-y-4">
      <div>
        <Link href="/" className="text-sm text-muted hover:text-foreground">
          ← 홈
        </Link>
        <h1 className="mt-1 text-xl font-bold">{brandTitle(brand.name, brand.nameKo)}</h1>
        <p className="text-sm text-muted">기종 {brand.cameras.length}개</p>
      </div>

      {/* 휴대폰 2칸, 데스크톱 4칸 (ARCHITECTURE.md 2.1) */}
      <ul className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {brand.cameras.map((c) => (
          <li key={c.slug}>
            <Link href={`/cameras/${c.slug}`} className="block overflow-hidden rounded-lg border border-line bg-card">
              <Thumb src={c.coverThumbnailUrl} alt="" className="aspect-[4/3] w-full" />
              <div className="p-2">
                <div className="truncate text-sm font-medium">{cameraTitle(c.name, c.nameKo)}</div>
                <div className="text-xs text-muted">
                  {c.releaseYear ? `${c.releaseYear} · ` : ""}작례 {c.photoCount}장
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
