import type { Metadata } from "next";
import { Suspense } from "react";
import { loginUrl, safeRedirect } from "@/lib/client";

export const metadata: Metadata = { title: "로그인", robots: { index: false } };

export default function LoginPage({ searchParams }: PageProps<"/login">) {
  return (
    <div className="mx-auto max-w-sm space-y-4 py-10">
      <h1 className="text-xl font-bold">로그인</h1>
      <p className="text-sm text-muted">작례를 올리고 신고하려면 로그인이 필요해요.</p>
      <Suspense fallback={null}>
        <LoginButtons searchParams={searchParams} />
      </Suspense>
      <p className="text-xs text-muted">
        처음이면 닉네임을 정하고 약관에 동의한 뒤 가입돼요. 이메일 등 다른 정보는 받지 않아요.
      </p>
    </div>
  );
}

async function LoginButtons({ searchParams }: Pick<PageProps<"/login">, "searchParams">) {
  const sp = await searchParams;
  const redirect = safeRedirect(typeof sp.redirect === "string" ? sp.redirect : "/");
  const failed = sp.error === "oauth";

  return (
    <div className="space-y-3">
      {failed && <p className="text-sm text-red-600">로그인하지 못했어요. 다시 시도해 주세요.</p>}
      {/* 백엔드로 바로 이동하는 링크라 <a>를 쓴다 (Next 페이지 이동이 아님) */}
      <a
        href={loginUrl("google", redirect)}
        className="block w-full rounded-lg border border-line bg-card py-3 text-center font-medium hover:border-foreground"
      >
        구글로 계속하기
      </a>
      <a
        href={loginUrl("kakao", redirect)}
        className="block w-full rounded-lg bg-[#FEE500] py-3 text-center font-medium text-black/85 hover:brightness-95"
      >
        카카오로 계속하기
      </a>
    </div>
  );
}
