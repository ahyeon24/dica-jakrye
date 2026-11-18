"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiSend, ClientApiError } from "@/lib/client";
import { Thumb } from "./Thumb";

type ReportItem = {
  id: number;
  reason: string;
  reasonLabel: string;
  detail: string | null;
  reportedAt: string;
  reporterNickname: string;
};

type PhotoReports = {
  photoId: number;
  source: "UPLOAD" | "COMMONS";
  thumbnailUrl: string;
  hidden: boolean;
  hiddenAt: string | null;
  cameraSlug: string | null;
  cameraName: string | null;
  credit: string | null;
  firstReportedAt: string;
  reports: ReportItem[];
};

const DAY = 24 * 60 * 60 * 1000;

function formatDate(iso: string) {
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** 미처리 신고 목록. 로그인 안 했으면 "login", 관리자가 아니면 "forbidden" */
async function fetchReports(): Promise<PhotoReports[] | "login" | "forbidden" | "error"> {
  const res = await fetch("/api/admin/reports", { credentials: "same-origin" }).catch(() => null);
  if (!res) return "error";
  if (res.status === 401) return "login";
  if (res.status === 403) return "forbidden";
  if (!res.ok) return "error";
  return (await res.json()) as PhotoReports[];
}

/** 관리자 신고 처리 화면 (PRD F8). 작례별로 묶어 먼저 신고된 것부터 */
export function AdminReports() {
  // undefined = 불러오는 중
  const [data, setData] = useState<PhotoReports[] | "login" | "forbidden" | "error" | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchReports().then((result) => {
      if (!cancelled) setData(result);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  async function act(photoId: number, action: "delete" | "reject" | "change-camera", label: string) {
    let body: unknown;
    if (action === "change-camera") {
      const slug = window.prompt("바꿀 기종의 주소 이름(slug)을 넣어 주세요. 예: canon-ixus-30");
      if (!slug) return;
      body = { cameraSlug: slug.trim() };
    } else if (!window.confirm(`${label}할까요?`)) {
      return;
    }
    setError(null);
    try {
      await apiSend("POST", `/api/admin/reports/photos/${photoId}/${action}`, body);
      setData(await fetchReports());
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "처리하지 못했어요.");
    }
  }

  if (data === undefined) return <p className="text-sm text-muted">불러오는 중…</p>;
  if (data === "login") {
    return (
      <Link href="/login?redirect=%2Fadmin%2Freports" className="text-sm underline">
        로그인하기
      </Link>
    );
  }
  if (data === "forbidden") return <p className="text-sm text-muted">관리자만 볼 수 있어요.</p>;
  if (data === "error") return <p className="text-sm text-muted">불러오지 못했어요. 새로고침해 주세요.</p>;

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        미처리 {data.length}건. 초상권·도용 신고로 가린 작례는 <b>30일 안에</b> 삭제하거나 기각해야 해요.
      </p>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {data.length === 0 && <p className="rounded-lg border border-line px-4 py-10 text-center">처리할 신고가 없어요</p>}
      <ul className="space-y-3">
        {data.map((p) => {
          const deadline = p.hiddenAt ? new Date(new Date(p.hiddenAt).getTime() + 30 * DAY) : null;
          return (
            <li key={p.photoId} className="flex gap-3 rounded-lg border border-line p-3">
              <Link href={`/photos/${p.photoId}`} className="shrink-0">
                <Thumb src={p.thumbnailUrl} alt="신고된 작례" className="h-24 w-24 rounded" />
              </Link>
              <div className="min-w-0 flex-1 space-y-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">작례 {p.photoId}</span>
                  <span className="text-muted">
                    {p.cameraName ?? "미분류"} · {p.source === "COMMONS" ? "Commons" : "업로드"} · {p.credit}
                  </span>
                  {p.hidden && deadline && (
                    <span className="rounded bg-red-600 px-1.5 text-xs text-white">
                      가림 · 기한 {formatDate(deadline.toISOString())}
                    </span>
                  )}
                </div>
                <ul className="space-y-1">
                  {p.reports.map((r) => (
                    <li key={r.id}>
                      <b>{r.reasonLabel}</b> <span className="text-muted">· {r.reporterNickname} · {formatDate(r.reportedAt)}</span>
                      {r.detail && <p className="whitespace-pre-wrap break-words text-muted">{r.detail}</p>}
                    </li>
                  ))}
                </ul>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => act(p.photoId, "delete", "작례를 삭제")}
                    className="rounded bg-red-600 px-3 py-1 font-medium text-white"
                  >
                    삭제
                  </button>
                  <button
                    type="button"
                    onClick={() => act(p.photoId, "reject", p.hidden ? "기각하고 다시 공개" : "기각")}
                    className="rounded border border-line px-3 py-1"
                  >
                    기각{p.hidden ? " (다시 공개)" : ""}
                  </button>
                  <button
                    type="button"
                    onClick={() => act(p.photoId, "change-camera", "기종 변경")}
                    className="rounded border border-line px-3 py-1"
                  >
                    기종 변경
                  </button>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
