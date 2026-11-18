"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { PhotoDetail } from "@/lib/types";
import { PhotoViewer } from "./PhotoViewer";

/**
 * 서버에서 못 찾은 작례를 브라우저에서 한 번 더 찾아본다.
 * 신고로 가려진 작례는 올린 사람과 관리자에게만 보이는데, 서버 렌더링은 로그인 쿠키 없이 불러서 못 찾기 때문이다.
 */
export function ClientPhotoFallback({ id, query }: { id: string; query: string }) {
  // undefined = 찾는 중, null = 없음
  const [photo, setPhoto] = useState<PhotoDetail | null | undefined>(undefined);

  useEffect(() => {
    fetch(`/api/photos/${id}`, { credentials: "same-origin" })
      .then((res) => (res.ok ? (res.json() as Promise<PhotoDetail>) : null))
      .then(setPhoto)
      .catch(() => setPhoto(null));
  }, [id]);

  if (photo === undefined) return <p className="text-sm text-muted">불러오는 중…</p>;
  if (photo === null) {
    return (
      <div className="space-y-2 py-16 text-center">
        <h1 className="text-lg font-bold">찾을 수 없는 작례예요</h1>
        <p className="text-sm text-muted">삭제됐거나 신고로 가려진 작례일 수 있어요.</p>
        <Link href="/" className="text-sm text-muted underline">
          홈으로
        </Link>
      </div>
    );
  }
  return <PhotoViewer key={photo.id} initial={photo} query={query} />;
}
