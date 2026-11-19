"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiSend, ClientApiError } from "@/lib/client";
import { cameraTitle } from "@/lib/format";
import type { BrandItem, SearchItem } from "@/lib/types";
import { Thumb } from "./Thumb";

type Candidate = {
  slug: string;
  name: string;
  nameKo: string | null;
  brandName: string;
  reason: "MAKER_MODEL_ID" | "SIMILAR_NAME";
};

type Group = {
  exifMake: string;
  exifModel: string;
  count: number;
  lastUploadedAt: string;
  makerModelId: string | null;
  maxWidth: number | null;
  maxHeight: number | null;
  brandSlug: string | null;
  samples: { id: number; thumbnailUrl: string }[];
  candidates: Candidate[];
};

type NewCamera = {
  brandSlug: string;
  name: string;
  nameKo: string;
  sensorType: "" | "CCD" | "CMOS";
  nativeWidth: string;
  nativeHeight: string;
  releaseYear: string;
};

const REASON_LABEL: Record<Candidate["reason"], string> = {
  MAKER_MODEL_ID: "같은 캐논 내부 번호",
  SIMILAR_NAME: "비슷한 이름",
};

async function fetchGroups(): Promise<Group[] | "login" | "forbidden" | "error"> {
  const res = await fetch("/api/admin/unclassified", { credentials: "same-origin" }).catch(() => null);
  if (!res) return "error";
  if (res.status === 401) return "login";
  if (res.status === 403) return "forbidden";
  if (!res.ok) return "error";
  return (await res.json()) as Group[];
}

/**
 * 미분류 작례 연결 (PRD F5). EXIF 모델명별로 묶어 많은 것부터 보여 준다.
 * 후보를 고르거나, 기종을 검색하거나, 새 기종을 만들어 연결한다. 연결하면 같은 모델명의 작례가 모두 옮겨지고
 * 다음부터 올라오는 사진도 자동으로 붙는다.
 */
export function AdminUnclassified() {
  const [data, setData] = useState<Group[] | "login" | "forbidden" | "error" | undefined>(undefined);
  const [brands, setBrands] = useState<BrandItem[]>([]);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchGroups().then((result) => {
      if (!cancelled) setData(result);
    });
    fetch("/api/brands")
      .then((res) => (res.ok ? (res.json() as Promise<BrandItem[]>) : []))
      .then((b) => {
        if (!cancelled) setBrands(b);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  async function link(group: Group, target: { cameraSlug: string; label: string } | { newCamera: NewCamera }) {
    const label = "cameraSlug" in target ? target.label : `새 기종 "${target.newCamera.name}"`;
    if (!window.confirm(`"${group.exifModel}" 작례 ${group.count}장을 ${label}에 연결할까요?\n앞으로 이 모델명으로 올라오는 사진도 자동으로 연결돼요.`)) {
      return;
    }
    const body =
      "cameraSlug" in target
        ? { exifMake: group.exifMake, exifModel: group.exifModel, cameraSlug: target.cameraSlug }
        : {
            exifMake: group.exifMake,
            exifModel: group.exifModel,
            newCamera: {
              brandSlug: target.newCamera.brandSlug,
              name: target.newCamera.name.trim(),
              nameKo: target.newCamera.nameKo.trim() || null,
              sensorType: target.newCamera.sensorType || null,
              nativeWidth: toInt(target.newCamera.nativeWidth),
              nativeHeight: toInt(target.newCamera.nativeHeight),
              releaseYear: toInt(target.newCamera.releaseYear),
            },
          };
    setMessage(null);
    try {
      const res = await apiSend<{ cameraSlug: string; moved: number }>("POST", "/api/admin/exif-models", body);
      setMessage({ text: `${res.moved}장을 ${res.cameraSlug}에 연결했어요.`, error: false });
      setData(await fetchGroups());
    } catch (e) {
      setMessage({ text: e instanceof ClientApiError ? e.message : "연결하지 못했어요.", error: true });
    }
  }

  if (data === undefined) return <p className="text-sm text-muted">불러오는 중…</p>;
  if (data === "login") {
    return (
      <Link href="/login?redirect=%2Fadmin%2Funclassified" className="text-sm underline">
        로그인하기
      </Link>
    );
  }
  if (data === "forbidden") return <p className="text-sm text-muted">관리자만 볼 수 있어요.</p>;
  if (data === "error") return <p className="text-sm text-muted">불러오지 못했어요. 새로고침해 주세요.</p>;

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        등록된 기종을 찾지 못한 작례 {data.reduce((n, g) => n + g.count, 0)}장. 연결하기 전에는 올린 사람 프로필에만
        보여요. 빈티지 디카가 아니면(폰·최신 카메라) 작례를 열어 삭제할 수 있어요.
      </p>
      {message && <p className={`text-sm ${message.error ? "text-red-600" : "text-green-700"}`}>{message.text}</p>}
      {data.length === 0 && <p className="rounded-lg border border-line px-4 py-10 text-center">미분류 작례가 없어요</p>}
      <ul className="space-y-3">
        {data.map((g) => (
          <GroupCard key={`${g.exifMake}\u0000${g.exifModel}`} group={g} brands={brands} onLink={(t) => link(g, t)} />
        ))}
      </ul>
    </div>
  );
}

function GroupCard({
  group,
  brands,
  onLink,
}: {
  group: Group;
  brands: BrandItem[];
  onLink: (target: { cameraSlug: string; label: string } | { newCamera: NewCamera }) => void;
}) {
  const [mode, setMode] = useState<"none" | "search" | "new">("none");

  return (
    <li className="space-y-3 rounded-lg border border-line p-3 text-sm">
      <div className="flex gap-2 overflow-x-auto">
        {group.samples.map((s) => (
          <Link key={s.id} href={`/photos/${s.id}`} className="shrink-0">
            <Thumb src={s.thumbnailUrl} alt="미분류 작례" className="h-20 w-20 rounded" />
          </Link>
        ))}
      </div>
      <div>
        <p className="font-medium break-all">
          {group.exifMake} / {group.exifModel}
        </p>
        <p className="text-muted">
          {group.count}장 · 최근 {group.lastUploadedAt.slice(0, 10).replaceAll("-", ".")}
          {group.maxWidth && group.maxHeight && ` · 가장 큰 사진 ${group.maxWidth}×${group.maxHeight}`}
          {group.makerModelId && ` · 캐논 내부 번호 ${group.makerModelId}`}
        </p>
      </div>

      {group.candidates.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {group.candidates.map((c) => {
            const label = `${c.brandName} ${cameraTitle(c.name, c.nameKo)}`;
            return (
              <button
                key={c.slug}
                type="button"
                onClick={() => onLink({ cameraSlug: c.slug, label })}
                className={`rounded px-3 py-1 ${c.reason === "MAKER_MODEL_ID" ? "bg-foreground text-background" : "border border-line"}`}
              >
                {label}에 연결 <span className="opacity-70">({REASON_LABEL[c.reason]})</span>
              </button>
            );
          })}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => setMode(mode === "search" ? "none" : "search")}
          className="rounded border border-line px-3 py-1"
        >
          다른 기종 찾기
        </button>
        <button
          type="button"
          onClick={() => setMode(mode === "new" ? "none" : "new")}
          className="rounded border border-line px-3 py-1"
        >
          새 기종으로 추가
        </button>
      </div>

      {mode === "search" && <CameraPicker onPick={(slug, label) => onLink({ cameraSlug: slug, label })} />}
      {mode === "new" && <NewCameraForm group={group} brands={brands} onSubmit={(newCamera) => onLink({ newCamera })} />}
    </li>
  );
}

/** 기종 검색해서 고르기. 공개 검색 API를 그대로 쓴다 */
function CameraPicker({ onPick }: { onPick: (slug: string, label: string) => void }) {
  const [q, setQ] = useState("");
  const [result, setResult] = useState<{ query: string; items: SearchItem[] } | null>(null);
  const query = q.trim();
  const items = result && result.query === query ? result.items : [];

  useEffect(() => {
    if (!query) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`/api/cameras/search?q=${encodeURIComponent(query.slice(0, 50))}`, { signal: controller.signal })
        .then((res) => (res.ok ? (res.json() as Promise<{ items: SearchItem[] }>) : { items: [] }))
        .then((data) => setResult({ query, items: data.items }))
        .catch(() => {});
    }, 200);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  return (
    <div className="space-y-2">
      <input
        type="search"
        value={q}
        maxLength={50}
        onChange={(e) => setQ(e.target.value)}
        placeholder="기종 이름 (예: 익서스 30, IXY)"
        aria-label="연결할 기종 검색"
        className="w-full rounded border border-line bg-card px-3 py-2"
      />
      {items.length > 0 && (
        <ul className="divide-y divide-line rounded border border-line">
          {items.map((it) => {
            const label = `${it.brandName} ${cameraTitle(it.name, it.nameKo)}`;
            return (
              <li key={it.slug}>
                <button
                  type="button"
                  onClick={() => onPick(it.slug, label)}
                  className="w-full px-3 py-2 text-left hover:bg-line/40"
                >
                  {label} <span className="text-muted">· {it.photoCount}장</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/** 새 기종 만들기. EXIF 값으로 미리 채우고, 관리자가 확인해서 고친다 */
function NewCameraForm({
  group,
  brands,
  onSubmit,
}: {
  group: Group;
  brands: BrandItem[];
  onSubmit: (c: NewCamera) => void;
}) {
  const brandSlug = group.brandSlug ?? brands[0]?.slug ?? "";
  const brandName = brands.find((b) => b.slug === brandSlug)?.name ?? "";
  const [form, setForm] = useState<NewCamera>({
    brandSlug,
    name: stripBrand(group.exifModel, brandName),
    nameKo: "",
    sensorType: "",
    // 카메라 해상도는 가로 기준. 세로 사진만 있어도 긴 변을 가로로 넣는다
    nativeWidth: group.maxWidth && group.maxHeight ? String(Math.max(group.maxWidth, group.maxHeight)) : "",
    nativeHeight: group.maxWidth && group.maxHeight ? String(Math.min(group.maxWidth, group.maxHeight)) : "",
    releaseYear: "",
  });
  const set = (k: keyof NewCamera) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  const input = "w-full rounded border border-line bg-card px-3 py-2";

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (form.name.trim()) onSubmit(form);
      }}
      className="grid gap-2 sm:grid-cols-2"
    >
      <label className="space-y-1">
        <span className="text-muted">브랜드</span>
        <select value={form.brandSlug} onChange={set("brandSlug")} className={input}>
          {brands.map((b) => (
            <option key={b.slug} value={b.slug}>
              {b.name}
              {b.nameKo ? ` (${b.nameKo})` : ""}
            </option>
          ))}
        </select>
      </label>
      <label className="space-y-1">
        <span className="text-muted">이름 (브랜드 빼고)</span>
        <input value={form.name} onChange={set("name")} maxLength={100} required className={input} />
      </label>
      <label className="space-y-1">
        <span className="text-muted">한글 이름 (선택)</span>
        <input value={form.nameKo} onChange={set("nameKo")} maxLength={100} placeholder="예: 익서스 145" className={input} />
      </label>
      <label className="space-y-1">
        <span className="text-muted">센서 (선택)</span>
        <select value={form.sensorType} onChange={set("sensorType")} className={input}>
          <option value="">모름</option>
          <option value="CCD">CCD</option>
          <option value="CMOS">CMOS</option>
        </select>
      </label>
      <label className="space-y-1">
        <span className="text-muted">원래 해상도 (선택)</span>
        <span className="flex items-center gap-1">
          <input value={form.nativeWidth} onChange={set("nativeWidth")} inputMode="numeric" className={input} />×
          <input value={form.nativeHeight} onChange={set("nativeHeight")} inputMode="numeric" className={input} />
        </span>
        <span className="block text-xs text-muted">올라온 사진 중 가장 큰 크기로 채웠어요. 줄인 사진이면 고쳐 주세요.</span>
      </label>
      <label className="space-y-1">
        <span className="text-muted">출시 연도 (선택)</span>
        <input value={form.releaseYear} onChange={set("releaseYear")} inputMode="numeric" className={input} />
      </label>
      <div className="sm:col-span-2">
        <button type="submit" className="rounded bg-foreground px-3 py-1.5 font-medium text-background">
          만들고 연결하기
        </button>
      </div>
    </form>
  );
}

/** "Canon IXUS 145" → "IXUS 145" */
function stripBrand(model: string, brandName: string) {
  if (!brandName) return model;
  const re = new RegExp(`^${brandName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s+`, "i");
  return model.replace(re, "");
}

function toInt(s: string) {
  const n = Number.parseInt(s.trim(), 10);
  return Number.isFinite(n) ? n : null;
}
