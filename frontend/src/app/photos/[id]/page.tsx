import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ViewPing } from "@/components/ViewPing";
import { apiGet } from "@/lib/api";
import { cameraTitle } from "@/lib/format";
import type { PhotoDetail } from "@/lib/types";

const DISPLAY_LONG_SIDE = 1600; // 보기용 이미지 긴 변 (백엔드 ImageProcessor와 같음)

export default function PhotoPage({ params }: PageProps<"/photos/[id]">) {
  return (
    <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
      <PhotoContent params={params} />
    </Suspense>
  );
}

async function PhotoContent({ params }: Pick<PageProps<"/photos/[id]">, "params">) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  const photo = await apiGet<PhotoDetail>(`/api/photos/${id}`);
  if (!photo) notFound();

  const size = displaySize(photo.width, photo.height);
  const exifRows = exifList(photo);

  return (
    <div className="space-y-4">
      <ViewPing photoId={photo.id} />

      {photo.camera ? (
        <Link href={`/cameras/${photo.camera.slug}`} className="text-sm text-muted hover:text-foreground">
          ← {photo.camera.brandName} {cameraTitle(photo.camera.name, photo.camera.nameKo)}
        </Link>
      ) : (
        <span className="text-sm text-muted">미분류 작례</span>
      )}

      {/* 1600px보다 작은 사진은 늘리지 않는다. 화면보다 크면 화면에 맞춰 줄인다. */}
      <figure className="space-y-2">
        <div className="flex justify-center bg-line/40">
          {/* eslint-disable-next-line @next/next/no-img-element -- 이미 1600px로 줄여 둔 이미지 */}
          <img
            src={photo.displayUrl}
            alt={photo.camera ? `${photo.camera.name}로 찍은 작례` : "작례"}
            width={size?.width}
            height={size?.height}
            decoding="async"
            className="h-auto max-h-[85vh] w-auto max-w-full object-contain"
          />
        </div>
        <figcaption className="text-xs text-muted">
          <Credit photo={photo} />
          {photo.isOriginalSize === false && (
            <span className="ml-2 rounded bg-foreground px-1 text-background">줄인 사진</span>
          )}
        </figcaption>
      </figure>

      {exifRows.length > 0 && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          {exifRows.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-muted">{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      )}

      {photo.isOriginalSize === false && (
        <p className="text-xs text-muted">
          원래 카메라 해상도보다 작은 사진이에요. 화질은 실제보다 낮거나 다르게 보일 수 있어요.
        </p>
      )}
    </div>
  );
}

/** 출처 표시. Commons는 작가·라이선스(링크)·원본 파일 페이지·크기 조정 여부를 반드시 적는다 (API.md 5장) */
function Credit({ photo }: { photo: PhotoDetail }) {
  if (photo.commons) {
    const c = photo.commons;
    return (
      <span>
        사진: {c.author} ·{" "}
        {c.licenseUrl ? (
          <a href={c.licenseUrl} target="_blank" rel="noopener noreferrer license" className="underline">
            {c.licenseName}
          </a>
        ) : (
          c.licenseName
        )}{" "}
        ·{" "}
        <a href={c.pageUrl} target="_blank" rel="noopener noreferrer" className="underline">
          Wikimedia Commons
        </a>
        {c.modified && " · 크기 조정됨"}
      </span>
    );
  }
  if (photo.uploader) {
    return <span>올린 사람: {photo.uploader.nickname}</span>;
  }
  return null;
}

function displaySize(w: number | null, h: number | null) {
  if (!w || !h) return null;
  const scale = Math.min(1, DISPLAY_LONG_SIDE / Math.max(w, h));
  return { width: Math.round(w * scale), height: Math.round(h * scale) };
}

/** EXIF에 있는 값만 보여준다 */
function exifList(p: PhotoDetail): [string, string][] {
  const e = p.exif;
  const rows: [string, string | null][] = [
    ["기종", p.camera ? `${p.camera.brandName} ${p.camera.name}` : null],
    ["촬영일", e.takenAt ? e.takenAt.slice(0, 16).replace("T", " ").replaceAll("-", ".") : null],
    ["플래시", e.flashFired == null ? null : e.flashFired ? "터짐" : "안 터짐"],
    ["조리개", e.fNumber != null ? `f/${e.fNumber}` : null],
    ["셔터", e.exposureTime ? `${e.exposureTime}초` : null],
    ["ISO", e.iso != null ? String(e.iso) : null],
    ["초점거리", e.focalLength != null ? `${e.focalLength}mm` : null],
    ["크기", p.width && p.height ? `${p.width}×${p.height}` : null],
  ];
  return rows.filter((r): r is [string, string] => r[1] !== null);
}
