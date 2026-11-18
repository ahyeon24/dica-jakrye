"use client";

import Link from "next/link";
import { useState } from "react";
import { apiSend, ClientApiError } from "@/lib/client";

const REASONS = [
  { value: "INAPPROPRIATE", label: "부적절한 사진" },
  { value: "STOLEN", label: "내가 찍은 사진을 도용함" },
  { value: "PORTRAIT_RIGHTS", label: "나(또는 아는 사람)가 동의 없이 나옴" },
  { value: "WRONG_CAMERA", label: "이 기종으로 찍은 사진이 아님" },
  { value: "OTHER", label: "기타" },
] as const;

type Mode = "menu" | "confirmDelete" | "report" | "needLogin" | "reported";

/**
 * 작례 ⋯ 메뉴 (와이어프레임 3쪽)
 * - 내 작례: 삭제하기 (한 번 더 확인)
 * - 남의 작례: 신고하기. 초상권·도용 신고는 접수하면 바로 가려진다 (PRD F8)
 */
export function PhotoMenu({
  photoId,
  isMine,
  onDelete,
  onHiddenByReport,
}: {
  photoId: number;
  isMine: boolean;
  onDelete: () => Promise<void>;
  onHiddenByReport: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<Mode>("menu");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState<string>("");
  const [detail, setDetail] = useState("");
  const [hidden, setHidden] = useState(false);

  function toggle() {
    setOpen((v) => !v);
    setMode("menu");
    setError(null);
  }

  async function startReport() {
    setError(null);
    // 로그인한 사람만 신고할 수 있다
    const res = await fetch("/api/me", { credentials: "same-origin" }).catch(() => null);
    setMode(res?.ok ? "report" : "needLogin");
  }

  async function confirmDelete() {
    setBusy(true);
    setError(null);
    try {
      await onDelete();
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "지우지 못했어요. 다시 시도해 주세요.");
      setBusy(false);
    }
  }

  async function submitReport() {
    if (!reason) return;
    setBusy(true);
    setError(null);
    try {
      const result = await apiSend<{ hidden: boolean }>("POST", `/api/photos/${photoId}/reports`, {
        reason,
        detail: detail.trim() || undefined,
      });
      setHidden(result.hidden);
      setMode("reported");
    } catch (e) {
      setError(e instanceof ClientApiError ? e.message : "신고하지 못했어요. 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }

  const detailRequired = reason === "OTHER";
  const canSubmit = !!reason && (!detailRequired || detail.trim().length > 0) && detail.length <= 500 && !busy;

  return (
    <div className="relative">
      <button
        type="button"
        aria-label="작례 메뉴"
        aria-expanded={open}
        onClick={toggle}
        className="rounded px-2 text-lg leading-none text-muted hover:text-foreground"
      >
        ⋯
      </button>
      {open && (
        <div className="absolute right-0 z-20 mt-1 w-72 rounded-lg border border-line bg-card p-1 text-sm shadow-lg">
          {mode === "menu" &&
            (isMine ? (
              <button
                type="button"
                onClick={() => setMode("confirmDelete")}
                className="w-full rounded px-3 py-2 text-left text-red-600 hover:bg-background"
              >
                삭제하기
              </button>
            ) : (
              <button type="button" onClick={startReport} className="w-full rounded px-3 py-2 text-left hover:bg-background">
                신고하기
              </button>
            ))}

          {mode === "confirmDelete" && (
            <div className="space-y-2 p-2">
              <p>이 작례를 지울까요? 되돌릴 수 없어요.</p>
              {error && <p className="text-xs text-red-600">{error}</p>}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={confirmDelete}
                  disabled={busy}
                  className="flex-1 rounded bg-red-600 py-1.5 font-medium text-white disabled:opacity-50"
                >
                  {busy ? "지우는 중…" : "지우기"}
                </button>
                <button
                  type="button"
                  onClick={() => setMode("menu")}
                  disabled={busy}
                  className="flex-1 rounded border border-line py-1.5"
                >
                  취소
                </button>
              </div>
            </div>
          )}

          {mode === "needLogin" && (
            <div className="space-y-2 p-2">
              <p>신고하려면 로그인이 필요해요.</p>
              <Link
                href={`/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`}
                className="block rounded bg-foreground py-1.5 text-center font-medium text-background"
              >
                로그인하기
              </Link>
              <p className="text-xs text-muted">
                회원이 아니어도{" "}
                <a href="mailto:dicajakrye@gmail.com" className="underline">
                  dicajakrye@gmail.com
                </a>
                으로 신고할 수 있어요.
              </p>
            </div>
          )}

          {mode === "report" && (
            <div className="space-y-2 p-2">
              <p className="font-medium">이 작례를 신고할까요?</p>
              <fieldset className="space-y-1">
                <legend className="sr-only">신고 사유</legend>
                {REASONS.map((r) => (
                  <label key={r.value} className="flex items-center gap-2">
                    <input
                      type="radio"
                      name={`reason-${photoId}`}
                      value={r.value}
                      checked={reason === r.value}
                      onChange={() => setReason(r.value)}
                    />
                    {r.label}
                  </label>
                ))}
              </fieldset>
              <textarea
                value={detail}
                onChange={(e) => setDetail(e.target.value)}
                maxLength={500}
                rows={3}
                placeholder={detailRequired ? "자세한 내용 (기타는 필수)" : "자세한 내용 (선택)"}
                className="w-full rounded border border-line bg-background px-2 py-1"
              />
              {(reason === "STOLEN" || reason === "PORTRAIT_RIGHTS") && (
                <p className="text-xs text-muted">이 사유는 접수하면 운영자가 확인할 때까지 작례를 바로 가려요.</p>
              )}
              {error && <p className="text-xs text-red-600">{error}</p>}
              <button
                type="button"
                onClick={submitReport}
                disabled={!canSubmit}
                className="w-full rounded bg-foreground py-1.5 font-medium text-background disabled:opacity-40"
              >
                {busy ? "보내는 중…" : "신고하기"}
              </button>
            </div>
          )}

          {mode === "reported" && (
            <div className="space-y-2 p-2">
              <p className="font-medium">신고가 접수됐어요.</p>
              <p className="text-muted">
                {hidden ? "운영자가 확인할 때까지 이 작례를 가렸어요." : "운영자가 확인한 뒤 처리할게요."}
              </p>
              <button
                type="button"
                onClick={() => (hidden ? onHiddenByReport() : setOpen(false))}
                className="w-full rounded border border-line py-1.5"
              >
                {hidden ? "돌아가기" : "닫기"}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
