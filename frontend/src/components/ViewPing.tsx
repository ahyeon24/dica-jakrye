"use client";

import { useEffect, useRef } from "react";

/** 브라우저에서 상세 화면이 열리면 조회수를 한 번 올린다. 서버 렌더링(검색봇)에서는 부르지 않는다. */
export function ViewPing({ photoId }: { photoId: number }) {
  const sent = useRef(false);
  useEffect(() => {
    if (sent.current) return;
    sent.current = true;
    // 실패해도 화면에는 영향이 없으므로 무시한다
    fetch(`/api/photos/${photoId}/views`, { method: "POST", keepalive: true }).catch(() => {});
  }, [photoId]);
  return null;
}
