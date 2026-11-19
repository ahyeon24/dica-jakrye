"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { ClientApiError, uploadPhoto } from "@/lib/client";
import { cameraTitle } from "@/lib/format";
import type { PhotoDetail } from "@/lib/types";

const MAX_BYTES = 20 * 1024 * 1024;

type ExifMatch = {
  matched: boolean;
  camera: { slug: string; name: string; nameKo: string | null; brandName: string } | null;
};

/** 고른 파일을 살펴본 결과 */
type Picked =
  | { kind: "error"; message: string }
  | { kind: "noExif"; previewUrl: string }
  | { kind: "ok"; file: File; previewUrl: string; make: string; model: string; match: ExifMatch | null };

/** 폰 사진 같으면 경고만 한다. 막지는 않는다 (와이어프레임 4쪽) */
function looksLikePhone(make: string, model: string): boolean {
  const m = `${make} ${model}`.toLowerCase();
  return /apple|iphone|pixel|galaxy|\bsm-[a-z]\d|xiaomi|redmi|oneplus|huawei|oppo|vivo/.test(m);
}

/**
 * 작례 올리기 (API.md 업로드 흐름)
 * 1. 파일 고르기 → 브라우저에서 EXIF를 읽어 기종을 미리 보여준다 (최종 판단은 서버)
 * 2. 동의 체크 → 업로드 → 작례 상세로 이동
 */
export function UploadForm() {
  const router = useRouter();
  const inputId = useId();
  const [loggedIn, setLoggedIn] = useState<boolean | undefined>(undefined);
  const [picked, setPicked] = useState<Picked | null>(null);
  const [reading, setReading] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 로그인이 필요하다. 안 했으면 로그인 후 이 화면으로 돌아오게 한다
  useEffect(() => {
    fetch("/api/me", { credentials: "same-origin" })
      .then((res) => {
        if (res.status === 401) router.replace(`/login?redirect=${encodeURIComponent("/upload")}`);
        setLoggedIn(res.ok);
      })
      .catch(() => setLoggedIn(false));
  }, [router]);

  // 미리보기 주소는 다 쓰면 풀어 준다
  const previewUrl = picked && picked.kind !== "error" ? picked.previewUrl : null;
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  async function onPick(file: File | undefined) {
    setError(null);
    setAgreed(false);
    if (!file) return;
    if (file.size > MAX_BYTES) {
      setPicked({ kind: "error", message: "20MB보다 큰 파일은 올릴 수 없어요." });
      return;
    }
    if (file.type && file.type !== "image/jpeg") {
      setPicked({ kind: "error", message: "JPEG 사진만 올릴 수 있어요." });
      return;
    }
    setReading(true);
    try {
      const exifr = await import("exifr");
      const tags = (await exifr.parse(file, ["Make", "Model"]).catch(() => null)) as
        | { Make?: string; Model?: string }
        | null
        | undefined;
      const make = tags?.Make?.replace(/\0/g, "").trim();
      const model = tags?.Model?.replace(/\0/g, "").trim();
      const url = URL.createObjectURL(file);
      if (!make || !model) {
        setPicked({ kind: "noExif", previewUrl: url });
        return;
      }
      let match: ExifMatch | null = null;
      try {
        const res = await fetch(
          `/api/cameras/exif-match?make=${encodeURIComponent(make.slice(0, 100))}&model=${encodeURIComponent(model.slice(0, 100))}`,
          { credentials: "same-origin" },
        );
        if (res.ok) match = (await res.json()) as ExifMatch;
      } catch {
        // 미리보기를 못 해도 올릴 수는 있다 (서버가 다시 판단)
      }
      setPicked({ kind: "ok", file, previewUrl: url, make, model, match });
    } finally {
      setReading(false);
    }
  }

  async function submit() {
    if (!picked || picked.kind !== "ok" || !agreed) return;
    setSubmitting(true);
    setError(null);
    try {
      const photo = await uploadPhoto<PhotoDetail>(picked.file, true);
      router.push(`/photos/${photo.id}`);
    } catch (err) {
      if (err instanceof ClientApiError && err.status === 401) {
        router.replace(`/login?redirect=${encodeURIComponent("/upload")}`);
        return;
      }
      setError(err instanceof ClientApiError ? err.message : "올리지 못했어요. 잠시 후 다시 시도해 주세요.");
      setSubmitting(false);
    }
  }

  if (loggedIn !== true) return <p className="text-sm text-muted">확인하는 중…</p>;

  return (
    <div className="space-y-4">
      <label
        htmlFor={inputId}
        className="block cursor-pointer rounded-lg border border-dashed border-line px-4 py-8 text-center hover:border-foreground"
      >
        <span className="font-medium">{picked ? "다른 사진 고르기" : "사진 고르기"}</span>
        <span className="mt-1 block text-xs text-muted">JPEG · 최대 20MB</span>
        <input
          id={inputId}
          type="file"
          accept="image/jpeg"
          className="sr-only"
          onChange={(e) => {
            onPick(e.target.files?.[0]);
            e.target.value = ""; // 같은 파일을 다시 골라도 동작하게
          }}
        />
      </label>

      {!picked && !reading && (
        <p className="text-sm text-muted">
          카메라에서 옮긴 <b>원본 파일</b>을 올려 주세요. 인스타·카톡으로 받은 파일은 촬영 정보가 지워져 있어서 올릴 수
          없어요.
        </p>
      )}
      {reading && <p className="text-sm text-muted">사진을 살펴보는 중…</p>}

      {picked?.kind === "error" && <p className="text-sm text-red-600">{picked.message}</p>}

      {picked?.kind === "noExif" && (
        <div className="space-y-2">
          <Preview url={picked.previewUrl} />
          <div className="rounded-lg border border-line p-3 text-sm">
            <p className="font-medium">촬영 정보가 없는 파일이에요</p>
            <p className="text-muted">카메라 메모리카드에서 옮긴 원본을 골라 주세요.</p>
          </div>
        </div>
      )}

      {picked?.kind === "ok" && (
        <div className="space-y-3">
          <Preview url={picked.previewUrl} />
          <div className="rounded-lg border border-line p-3 text-sm">
            {picked.match?.matched && picked.match.camera ? (
              <p className="font-medium">
                {cameraTitle(picked.match.camera.name, picked.match.camera.nameKo)}로 찍은 사진이에요
              </p>
            ) : looksLikePhone(picked.make, picked.model) ? (
              <>
                <p className="font-medium">휴대폰으로 찍은 사진 같아요</p>
                <p className="text-muted">빈티지 디카로 찍은 사진만 올려 주세요.</p>
              </>
            ) : (
              <>
                <p className="font-medium">아직 등록되지 않은 기종 같아요</p>
                <p className="text-muted">
                  올리면 서버가 한 번 더 찾아봐요. 그래도 못 찾으면 내 프로필에만 먼저 보이고, 운영자가 기종을 확인한
                  뒤 기종 페이지와 최근 올라온 작례에 나와요.
                </p>
              </>
            )}
            <p className="mt-1 text-xs text-muted">
              촬영 정보: {picked.make} {picked.model}
            </p>
          </div>
          <p className="text-xs text-muted">위치 정보(GPS)는 지우고 저장해요.</p>

          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-0.5" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
            <span>직접 찍은 사진이에요. 사람이 나온 사진은 당사자 동의를 받았어요.</span>
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="button"
            onClick={submit}
            disabled={!agreed || submitting}
            className="w-full rounded-lg bg-foreground py-3 font-medium text-background disabled:opacity-40"
          >
            {submitting ? "올리는 중…" : "올리기"}
          </button>
        </div>
      )}

      <p className="text-xs text-muted">
        올린 사진은 누구나 볼 수 있는 작례로 공개돼요. 원본 파일은 공개하지 않고, 화면에는 줄인 사진만 보여요.{" "}
        <Link href="/" className="underline">
          작례 둘러보기
        </Link>
      </p>
    </div>
  );
}

function Preview({ url }: { url: string }) {
  return (
    // eslint-disable-next-line @next/next/no-img-element -- 브라우저에만 있는 미리보기(blob:) 주소
    <img src={url} alt="고른 사진 미리보기" className="max-h-80 w-full rounded-lg bg-line object-contain" />
  );
}
