import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AuthMenu } from "@/components/AuthMenu";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "디카작례 — 빈티지 디카 작례 모음",
    template: "%s | 디카작례",
  },
  description: "빈티지 디카를 사기 전에, 그 기종으로 진짜 찍은 작례를 모아 보세요.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        <header className="border-b border-line">
          <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
            <Link href="/" className="text-lg font-bold tracking-tight">
              디카작례
            </Link>
            <div className="flex items-center gap-4">
              <Link href="/upload" className="text-sm font-medium hover:underline">
                올리기
              </Link>
              {/* 주소(usePathname)를 읽으므로 Suspense 안에 둔다 (Cache Components) */}
              <Suspense fallback={<span className="h-5 w-16" aria-hidden />}>
                <AuthMenu />
              </Suspense>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</main>

        <footer className="border-t border-line text-xs text-muted">
          <div className="mx-auto max-w-5xl space-y-1 px-4 py-6">
            {/* 개인정보처리방침·이용약관 페이지는 법적 고지(F9)를 만들 때 연결 */}
            <p>개인정보처리방침 · 이용약관</p>
            <p>일부 작례는 Wikimedia Commons의 자유 라이선스 사진입니다. 사진마다 작가와 라이선스를 표시합니다.</p>
            {/* 사진에 나온 사람은 회원이 아닐 수 있다. 로그인 없이 신고할 수 있는 창구 (PRD F8·F9) */}
            <p>
              초상권·저작권 침해 신고:{" "}
              <a href="mailto:dicajakrye@gmail.com" className="underline">
                dicajakrye@gmail.com
              </a>
            </p>
          </div>
        </footer>
      </body>
    </html>
  );
}
