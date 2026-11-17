import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { PhotoGrid } from "@/components/PhotoGrid";
import { apiGet } from "@/lib/api";
import { readFilters, toQuery, type PhotoFilters } from "@/lib/filters";
import { cameraTitle } from "@/lib/format";
import type { CameraDetail, Page, PhotoSummary } from "@/lib/types";

type Props = PageProps<"/cameras/[slug]">;

export default function CameraPage({ params, searchParams }: Props) {
  return (
    <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
      <CameraContent params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function CameraContent({ params, searchParams }: Pick<Props, "params" | "searchParams">) {
  const [{ slug }, sp] = await Promise.all([params, searchParams]);
  const filters = readFilters(sp);
  const query = toQuery(filters);

  const [camera, firstPage] = await Promise.all([
    apiGet<CameraDetail>(`/api/cameras/${encodeURIComponent(slug)}`),
    apiGet<Page<PhotoSummary>>(`/api/cameras/${encodeURIComponent(slug)}/photos${query ? `?${query}` : ""}`),
  ]);
  if (!camera || !firstPage) notFound();

  const href = (f: PhotoFilters) => {
    const q = toQuery(f);
    return `/cameras/${camera.slug}${q ? `?${q}` : ""}`;
  };
  const noFlashCount = Math.max(0, camera.photoCount - camera.flashPhotoCount);

  return (
    <div className="space-y-4">
      <div>
        <Link href={`/brands/${camera.brand.slug}`} className="text-sm text-muted hover:text-foreground">
          ← {camera.brand.nameKo ?? camera.brand.name}
        </Link>
        <h1 className="mt-1 text-xl font-bold">{cameraTitle(camera.name, camera.nameKo)}</h1>
        <p className="text-sm text-muted">
          {[camera.releaseYear, camera.sensorType, camera.nativeResolution, `작례 ${camera.photoCount}장`]
            .filter(Boolean)
            .join(" · ")}
        </p>
        {camera.aliases.length > 0 && (
          <p className="mt-1 text-xs text-muted">다른 이름: {camera.aliases.join(", ")}</p>
        )}
      </div>

      {/* 필터: URL에 남겨서 공유·뒤로 가기가 되게 한다 */}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Chip href={href({ ...filters, flash: undefined })} on={!filters.flash}>
          전체
        </Chip>
        <Chip href={href({ ...filters, flash: "true" })} on={filters.flash === "true"}>
          플래시 O <span className="text-muted">{camera.flashPhotoCount}</span>
        </Chip>
        <Chip href={href({ ...filters, flash: "false" })} on={filters.flash === "false"}>
          플래시 X <span className="text-muted">{noFlashCount}</span>
        </Chip>
        {camera.nativeResolution && (
          <Chip href={href({ ...filters, originalOnly: !filters.originalOnly })} on={filters.originalOnly}>
            {filters.originalOnly ? "✓ " : ""}원본 크기만 <span className="text-muted">{camera.originalSizePhotoCount}</span>
          </Chip>
        )}
      </div>

      {/* 필터가 바뀌면 격자를 새로 시작한다 */}
      <PhotoGrid key={query} slug={camera.slug} query={query} initial={firstPage} />
    </div>
  );
}

function Chip({ href, on, children }: { href: string; on: boolean; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      scroll={false}
      className={`rounded-full border px-3 py-1 ${on ? "border-foreground font-semibold" : "border-line hover:border-foreground"}`}
    >
      {children}
    </Link>
  );
}
