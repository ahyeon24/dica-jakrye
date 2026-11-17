"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { apiSend, ClientApiError, safeRedirect } from "@/lib/client";
import type { Me, SignupInfo } from "@/lib/types";

const NICKNAME_RULE = /^[가-힣A-Za-z0-9_]{2,20}$/;
const TERMS_LABEL: Record<SignupInfo["terms"][number]["type"], string> = {
  TERMS_OF_SERVICE: "이용약관",
  PRIVACY_COLLECTION: "개인정보 수집·이용",
};

type Availability = { nickname: string; available: boolean };

/** 소셜 로그인 후 처음 온 사람: 닉네임 + 약관 동의 → 가입 (API.md 2장) */
export function SignupForm() {
  const router = useRouter();
  // undefined = 불러오는 중, null = 가입 대기 정보 없음
  const [info, setInfo] = useState<SignupInfo | null | undefined>(undefined);
  const [nickname, setNickname] = useState("");
  const [agreed, setAgreed] = useState<Record<string, boolean>>({});
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/auth/signup", { credentials: "same-origin" })
      .then((res) => (res.ok ? (res.json() as Promise<SignupInfo>) : null))
      .then(setInfo)
      .catch(() => setInfo(null));
  }, []);

  const trimmed = nickname.trim();
  const ruleOk = NICKNAME_RULE.test(trimmed);

  // 입력이 멈추면 중복 확인
  useEffect(() => {
    if (!ruleOk) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`/api/users/nickname-availability?nickname=${encodeURIComponent(trimmed)}`, { signal: controller.signal })
        .then((res) => (res.ok ? (res.json() as Promise<{ available: boolean }>) : null))
        .then((data) => {
          if (data) setAvailability({ nickname: trimmed, available: data.available });
        })
        .catch(() => {});
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed, ruleOk]);

  if (info === undefined) return <p className="text-sm text-muted">불러오는 중…</p>;
  if (info === null) {
    return (
      <div className="space-y-2 text-sm">
        <p>가입 시간이 지났거나 로그인 정보가 없어요.</p>
        <Link href="/login" className="underline">
          다시 로그인하기
        </Link>
      </div>
    );
  }

  const checkedNow = availability?.nickname === trimmed ? availability.available : undefined;
  const allAgreed = info.terms.every((t) => agreed[t.type]);
  const canSubmit = ruleOk && checkedNow !== false && allAgreed && !submitting;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!info || !canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await apiSend<Me>("POST", "/api/auth/signup", {
        nickname: trimmed,
        agreedTerms: info.terms.map((t) => ({ type: t.type, version: t.version })),
      });
      router.replace(safeRedirect(info.redirect));
      router.refresh();
    } catch (err) {
      if (err instanceof ClientApiError && err.code === "SIGNUP_SESSION_NOT_FOUND") {
        setInfo(null);
      } else {
        setError(err instanceof ClientApiError ? err.message : "가입하지 못했어요. 다시 시도해 주세요.");
      }
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-5">
      <p className="text-sm text-muted">
        {info.provider === "KAKAO" ? "카카오" : "구글"} 계정으로 처음 오셨어요. 닉네임을 정하고 약관에 동의하면 가입돼요.
      </p>

      <div className="space-y-1">
        <label htmlFor="nickname" className="text-sm font-medium">
          닉네임
        </label>
        <input
          id="nickname"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          maxLength={20}
          autoComplete="off"
          className="w-full rounded-lg border border-line bg-card px-3 py-2 outline-none focus:border-foreground"
        />
        <p className="text-xs text-muted">
          {trimmed && !ruleOk && "2~20자의 한글·영문·숫자·밑줄(_)만 쓸 수 있어요."}
          {ruleOk && checkedNow === true && "쓸 수 있는 닉네임이에요."}
          {ruleOk && checkedNow === false && "이미 쓰고 있는 닉네임이에요."}
          {!trimmed && "프로필 주소에 쓰여요. 실명은 피하는 걸 권해요."}
        </p>
      </div>

      <fieldset className="space-y-2 rounded-lg border border-line p-3">
        <legend className="sr-only">약관 동의</legend>
        <label className="flex items-center gap-2 text-sm font-semibold">
          <input
            type="checkbox"
            checked={allAgreed}
            onChange={(e) => setAgreed(Object.fromEntries(info.terms.map((t) => [t.type, e.target.checked])))}
          />
          모두 동의
        </label>
        {info.terms.map((t) => (
          <label key={t.type} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={!!agreed[t.type]}
              onChange={(e) => setAgreed((prev) => ({ ...prev, [t.type]: e.target.checked }))}
            />
            <span>
              (필수) {TERMS_LABEL[t.type]}{" "}
              <a href={t.url} target="_blank" rel="noopener noreferrer" className="text-muted underline">
                보기
              </a>
            </span>
          </label>
        ))}
      </fieldset>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <button
        type="submit"
        disabled={!canSubmit}
        className="w-full rounded-lg bg-foreground py-3 font-medium text-background disabled:opacity-40"
      >
        {submitting ? "가입하는 중…" : "가입 완료"}
      </button>
      <p className="text-xs text-muted">동의하기 전에는 회원 정보가 저장되지 않아요.</p>
    </form>
  );
}
