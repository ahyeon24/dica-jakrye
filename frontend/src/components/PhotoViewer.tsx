"use client";

import Link from "next/link";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { apiSend } from "@/lib/client";
import { cameraTitle } from "@/lib/format";
import type { Adjacent, PhotoDetail } from "@/lib/types";

const DISPLAY_LONG_SIDE = 1600; // 보기용 이미지 긴 변 (백엔드 ImageProcessor와 같음)

/** undefined = 아직 모름(불러오는 중), null = 없음(처음·끝) */
type Neighbors = { prev: PhotoDetail | null | undefined; next: PhotoDetail | null | undefined };

/**
 * 작례 상세. 사진을 옆으로 넘기면 같은 기종의 이전·다음 작례로 바뀐다. (기종 격자와 같은 순서·필터)
 * - 휴대폰: 손가락으로 밀기 (가로 스크롤 + 스냅)
 * - 데스크톱: 화살표 버튼, 키보드 ← →
 * 넘길 때 페이지를 새로 불러오지 않고 주소만 바꾼다(replaceState). 뒤로 가기는 기종 격자로 간다.
 */
export function PhotoViewer({ initial, query }: { initial: PhotoDetail; query: string }) {
  const [current, setCurrent] = useState(initial);
  const [neighbors, setNeighbors] = useState<Neighbors>({ prev: undefined, next: undefined });
  const stripRef = useRef<HTMLDivElement>(null);
  const settleTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const pinged = useRef<number | null>(null);

  const slides = [neighbors.prev, current, neighbors.next].filter((p): p is PhotoDetail => !!p);
  const currentIndex = neighbors.prev ? 1 : 0;

  // 이전·다음 작례 불러오기
  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/photos/${current.id}/adjacent${query ? `?${query}` : ""}`, { signal: controller.signal })
      .then((res) => (res.ok ? (res.json() as Promise<Adjacent>) : null))
      .then((adj) => {
        if (adj) setNeighbors({ prev: adj.prev, next: adj.next });
      })
      .catch(() => {}); // 못 불러오면 넘기기만 안 될 뿐 화면은 그대로
    return () => controller.abort();
  }, [current.id, query]);

  // 조회수: 화면에 보인 작례마다 한 번
  useEffect(() => {
    if (pinged.current === current.id) return;
    pinged.current = current.id;
    apiSend("POST", `/api/photos/${current.id}/views`, undefined, { keepalive: true }).catch(() => {});
  }, [current.id]);

  // 앞에 사진이 끼어들어도 지금 사진이 화면 가운데 오도록 스크롤 위치를 맞춘다 (그리기 전에)
  useLayoutEffect(() => {
    const el = stripRef.current;
    if (el) el.scrollLeft = currentIndex * el.clientWidth;
  }, [current.id, currentIndex]);

  const go = useCallback(
    (target: PhotoDetail, direction: "prev" | "next") => {
      // 방금 보던 사진은 반대편 이웃이 된다. 다른 쪽 이웃은 불러올 때까지 모름
      setNeighbors(direction === "next" ? { prev: current, next: undefined } : { prev: undefined, next: current });
      setCurrent(target);
      window.history.replaceState(null, "", `/photos/${target.id}${query ? `?${query}` : ""}`);
    },
    [current, query],
  );

  // 스크롤이 멈추면 어느 사진에 멈췄는지 보고 바꾼다
  const onScroll = () => {
    clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      const el = stripRef.current;
      if (!el) return;
      const idx = Math.round(el.scrollLeft / el.clientWidth);
      const target = slides[idx];
      if (target && target.id !== current.id) go(target, idx < currentIndex ? "prev" : "next");
    }, 120);
  };

  const slideTo = useCallback(
    (direction: "prev" | "next") => {
      const el = stripRef.current;
      if (!el || !neighbors[direction]) return;
      const idx = currentIndex + (direction === "next" ? 1 : -1);
      el.scrollTo({ left: idx * el.clientWidth, behavior: "smooth" });
    },
    [currentIndex, neighbors],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
      if (e.key === "ArrowLeft") slideTo("prev");
      if (e.key === "ArrowRight") slideTo("next");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [slideTo]);

  const exifRows = exifList(current);

  return (
    <div className="space-y-4">
      {current.camera ? (
        <Link
          href={`/cameras/${current.camera.slug}${query ? `?${query}` : ""}`}
          className="text-sm text-muted hover:text-foreground"
        >
          ← {current.camera.brandName} {cameraTitle(current.camera.name, current.camera.nameKo)}
        </Link>
      ) : (
        <span className="text-sm text-muted">미분류 작례</span>
      )}

      <figure className="space-y-2">
        <div className="relative aspect-[4/3] max-h-[80vh] w-full bg-line/40">
          <div
            ref={stripRef}
            onScroll={onScroll}
            className="flex h-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain [scrollbar-width:none]"
          >
            {slides.map((p) => (
              <Slide key={p.id} photo={p} />
            ))}
          </div>
          <NavButton side="prev" show={!!neighbors.prev} onClick={() => slideTo("prev")} />
          <NavButton side="next" show={!!neighbors.next} onClick={() => slideTo("next")} />
        </div>
        <figcaption className="text-xs text-muted">
          <Credit photo={current} />
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
    </div>
  );
}

/** 사진 한 장. 1600px보다 작은 사진은 늘리지 않고, 칸보다 크면 칸에 맞춰 줄인다 */
function Slide({ photo }: { photo: PhotoDetail }) {
  const size = displaySize(photo.width, photo.height);
  return (
    <div className="flex h-full w-full shrink-0 snap-center items-center justify-center">
      {/* eslint-disable-next-line @next/next/no-img-element -- 이미 1600px로 줄여 둔 이미지 */}
      <img
        src={photo.displayUrl}
        alt={photo.camera ? `${photo.camera.name}로 찍은 작례` : "작례"}
        width={size?.width}
        height={size?.height}
        decoding="async"
        draggable={false}
        className="h-auto max-h-full w-auto max-w-full object-contain"
      />
    </div>
  );
}

function NavButton({ side, show, onClick }: { side: "prev" | "next"; show: boolean; onClick: () => void }) {
  if (!show) return null;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={side === "prev" ? "이전 작례" : "다음 작례"}
      className={`absolute top-1/2 hidden h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-xl text-white hover:bg-black/70 md:flex ${
        side === "prev" ? "left-2" : "right-2"
      }`}
    >
      {side === "prev" ? "‹" : "›"}
    </button>
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
